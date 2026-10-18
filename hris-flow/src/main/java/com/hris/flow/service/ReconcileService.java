package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.trace.TraceContext;
import com.hris.flow.entity.BizRelation;
import com.hris.flow.enums.InstanceStatus;
import com.hris.flow.mapper.BizRelationMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一致性对账（docs/04 §7，FLW-F-013）：扫描 flow_ext_biz_relation 与引擎状态漂移。
 *
 * <p>MVP 提供对账 service 方法 + 预留调度入口 {@link #reconcileAll()}。业务单状态漂移回写需
 * 各业务域 Feign 回调契约，属后续增量；本方法收敛「映射 RUNNING 但引擎已结束」的流程侧漂移。</p>
 */
@Service
public class ReconcileService {

    private static final Logger log = LoggerFactory.getLogger(ReconcileService.class);

    private final BizRelationMapper bizRelationMapper;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final EventOutboxService eventOutboxService;

    public ReconcileService(BizRelationMapper bizRelationMapper, RuntimeService runtimeService,
                            HistoryService historyService, EventOutboxService eventOutboxService) {
        this.bizRelationMapper = bizRelationMapper;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.eventOutboxService = eventOutboxService;
    }

    /**
     * 对账指定租户，返回发生漂移的流程实例 ID 列表。
     */
    public List<String> reconcile(Long tenantId) {
        TraceContext.setTenantId(tenantId);
        TraceContext.setUserId(0L);
        try {
            List<BizRelation> running = bizRelationMapper.selectList(new LambdaQueryWrapper<BizRelation>()
                    .eq(BizRelation::getTenantId, tenantId)
                    .eq(BizRelation::getStatus, InstanceStatus.RUNNING.name()));
            List<String> drift = new ArrayList<>();
            for (BizRelation rel : running) {
                ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(rel.getProcInstId())
                        .processInstanceTenantId(String.valueOf(tenantId))
                        .singleResult();
                if (pi != null) {
                    continue;
                }
                HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(rel.getProcInstId())
                        .processInstanceTenantId(String.valueOf(tenantId))
                        .singleResult();
                if (hpi != null && hpi.getEndTime() != null) {
                    rel.setStatus(InstanceStatus.FINISHED.name());
                    bizRelationMapper.updateById(rel);
                    drift.add(rel.getProcInstId());
                    eventOutboxService.publish(EventOutboxService.TOPIC_INSTANCE_FINISHED, tenantId,
                            rel.getBizKey(),
                            Map.of("procInstId", rel.getProcInstId(), "bizType", rel.getBizType(),
                                    "bizKey", rel.getBizKey(), "outcome", "RECONCILED"));
                } else {
                    log.warn("对账发现引擎侧缺失的映射: procInstId={}, bizKey={}", rel.getProcInstId(), rel.getBizKey());
                }
            }
            return drift;
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 预留调度入口（PLT-F-011）：遍历全租户执行对账。MVP 无租户清单来源（依赖 auth 域），暂空转。
     */
    public void reconcileAll() {
        log.warn("全租户对账调度入口未接入租户清单来源（auth 域），MVP 暂空转");
    }
}
