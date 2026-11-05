package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.PageResult;
import com.hris.flow.dto.TaskView;
import com.hris.flow.entity.BizRelation;
import com.hris.flow.entity.FlowCc;
import com.hris.flow.mapper.BizRelationMapper;
import com.hris.flow.mapper.FlowCcMapper;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审批抄送服务（FLW-F-011 一期深化）：启动抄送写入 / 补抄送 / 抄送我的分页查询。
 *
 * <p>存储 flow_ext_cc，uk(tenant_id, proc_inst_id, cc_node, emp_id) 防重复；
 * 重复写入按忽略处理（幂等）。租户条件由 MyBatis-Plus 租户插件自动注入。</p>
 */
@Service
public class CcService {

    private static final Logger log = LoggerFactory.getLogger(CcService.class);

    private final FlowCcMapper flowCcMapper;
    private final BizRelationMapper bizRelationMapper;
    private final TaskService taskService;

    public CcService(FlowCcMapper flowCcMapper, BizRelationMapper bizRelationMapper,
                     TaskService taskService) {
        this.flowCcMapper = flowCcMapper;
        this.bizRelationMapper = bizRelationMapper;
        this.taskService = taskService;
    }

    /** 实例启动时按发起节点写入抄送（重复忽略），返回实际新增条数。 */
    @Transactional(rollbackFor = Exception.class)
    public int recordOnStart(String procInstId, String nodeKey, List<Long> empIds) {
        return insertRows(procInstId, nodeKey, empIds);
    }

    /**
     * 补抄送：校验实例存在后按当前活动节点写入，返回实际新增条数（已抄送的忽略）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int addCc(String procInstId, List<Long> empIds) {
        requireTenant();
        BizRelation rel = bizRelationMapper.selectOne(new LambdaQueryWrapper<BizRelation>()
                .eq(BizRelation::getProcInstId, procInstId));
        if (rel == null) {
            throw new BizException(ErrorCode.FLOW_NOT_FOUND, "流程实例不存在");
        }
        return insertRows(procInstId, currentTaskKey(procInstId), empIds);
    }

    /** 抄送我的：当前用户 = emp_id，按抄送时间倒序分页，字段对齐 TaskView。 */
    public PageResult<TaskView> pageMine(long page, long size) {
        requireTenant();
        Long uid = TraceContext.getUserId();
        if (uid == null || uid == 0L) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少用户身份，无法查询抄送");
        }
        LambdaQueryWrapper<FlowCc> w = new LambdaQueryWrapper<FlowCc>()
                .eq(FlowCc::getEmpId, uid)
                .orderByDesc(FlowCc::getCreatedAt);
        long total = flowCcMapper.selectCount(w);
        List<FlowCc> rows = flowCcMapper.selectList(
                w.last("limit " + safeSize(size) + " offset " + offset(page, size)));
        Map<String, BizRelation> relMap = bizByInstIds(rows.stream()
                .map(FlowCc::getProcInstId).distinct().toList());
        List<TaskView> views = new ArrayList<>();
        for (FlowCc c : rows) {
            BizRelation rel = relMap.get(c.getProcInstId());
            String status = rel == null || rel.getStatus() == null ? "RUNNING" : rel.getStatus();
            String time = rel == null ? null : fmt(rel.getCreatedAt());
            views.add(new TaskView(null, c.getProcInstId(),
                    rel == null ? null : rel.getBizType(), rel == null ? null : rel.getBizType(),
                    rel == null ? null : rel.getBizKey(), c.getCcNode(), null, status,
                    time, time, null, 0L, null, false));
        }
        return new PageResult<>(views, page, size, total);
    }

    /** 写入抄送行：去重 + uk 兜底（DuplicateKeyException 忽略）。 */
    private int insertRows(String procInstId, String nodeKey, List<Long> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Long empId : new LinkedHashSet<>(empIds)) {
            if (empId == null || empId <= 0) {
                continue;
            }
            Long dup = flowCcMapper.selectCount(new LambdaQueryWrapper<FlowCc>()
                    .eq(FlowCc::getProcInstId, procInstId)
                    .eq(FlowCc::getCcNode, nodeKey)
                    .eq(FlowCc::getEmpId, empId));
            if (dup != null && dup > 0) {
                continue;
            }
            FlowCc cc = new FlowCc();
            cc.setProcInstId(procInstId);
            cc.setCcNode(nodeKey);
            cc.setEmpId(empId);
            try {
                flowCcMapper.insert(cc);
                n++;
            } catch (DuplicateKeyException e) {
                log.debug("抄送并发重复，忽略: procInstId={}, node={}, empId={}", procInstId, nodeKey, empId);
            }
        }
        return n;
    }

    /** 抄送节点：当前活动任务的节点 Key（start 后无任务时兜底 start）。 */
    private String currentTaskKey(String procInstId) {
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(procInstId).list();
        return tasks.isEmpty() ? "start" : tasks.get(0).getTaskDefinitionKey();
    }

    private Map<String, BizRelation> bizByInstIds(List<String> procInstIds) {
        if (procInstIds.isEmpty()) {
            return Map.of();
        }
        List<BizRelation> rels = bizRelationMapper.selectList(new LambdaQueryWrapper<BizRelation>()
                .in(BizRelation::getProcInstId, procInstIds));
        return rels.stream().collect(Collectors.toMap(BizRelation::getProcInstId, r -> r, (a, b) -> a));
    }

    private Long requireTenant() {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            throw new BizException(ErrorCode.TENANT_INVALID);
        }
        return tenantId;
    }

    private int offset(long page, long size) {
        long p = Math.max(1, page);
        return (int) ((p - 1) * Math.max(1, size));
    }

    private int safeSize(long size) {
        return (int) Math.min(Math.max(1, size), 200);
    }

    private String fmt(java.time.LocalDateTime d) {
        return d == null ? null : d.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
