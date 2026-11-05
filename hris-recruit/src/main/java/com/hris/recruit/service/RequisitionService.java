package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.RequisitionDtos.RequisitionCreateRequest;
import com.hris.recruit.dto.RequisitionDtos.RequisitionResponse;
import com.hris.recruit.dto.RequisitionDtos.RequisitionUpdateRequest;
import com.hris.recruit.entity.Requisition;
import com.hris.recruit.mapper.RequisitionMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 招聘需求单（Requisition）：招聘流程起点，记录部门/岗位/招聘人数/原因与状态，批准后驱动职位发布。
 *
 * <p>业务域：招聘流程（recruit）；分层：Service。</p>
 *
 * <p>状态机：DRAFT→APPROVING→OPEN/CLOSED。状态迁移仅两条路径：
 * submit 端点（DRAFT→APPROVING 并发起 RCS_REQUISITION 流程）与 flow 审批回调
 * （APPROVING→OPEN/CLOSED，见 RecruitApprovalCallbackService）；create/update 不接受 status
 * 入参，防止状态机旁路。</p>
 */
@Service
public class RequisitionService {

    private static final Logger log = LoggerFactory.getLogger(RequisitionService.class);

    /** 状态：草稿（可提交） */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态：审批中（流程已发起，等回调） */
    public static final String STATUS_APPROVING = "APPROVING";
    /** 状态：已开放（审批通过） */
    public static final String STATUS_OPEN = "OPEN";
    /** 状态：已关闭（审批拒绝或人工关闭） */
    public static final String STATUS_CLOSED = "CLOSED";

    private final RequisitionMapper mapper;
    private final FlowIntegration flowIntegration;

    public RequisitionService(RequisitionMapper mapper, FlowIntegration flowIntegration) {
        this.mapper = mapper;
        this.flowIntegration = flowIntegration;
    }

    /** 按 ID 查询，不存在抛 NOT_FOUND。 */
    public RequisitionResponse get(Long id) {
        Requisition e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Requisition 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询列表。 */
    public PageResult<RequisitionResponse> list(long page, long size) {
        IPage<Requisition> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<RequisitionResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /**
     * 新建记录（事务）。状态固定 DRAFT，不接受请求体状态（防状态机旁路）。
     */
    @Transactional(rollbackFor = Exception.class)
    public RequisitionResponse create(RequisitionCreateRequest req) {
        Requisition e = new Requisition();
        e.setReqNo(req.reqNo());
        e.setDeptId(req.deptId());
        e.setPositionId(req.positionId());
        e.setHeadcount(req.headcount());
        e.setReason(req.reason());
        e.setStatus(STATUS_DRAFT);
        e.setPublished(0);
        mapper.insert(e);
        return toResponse(e);
    }

    /**
     * 更新记录（事务）；数据被并发变更时抛 RCS_ERROR。状态字段不在请求体内，不经此迁移。
     */
    @Transactional(rollbackFor = Exception.class)
    public RequisitionResponse update(Long id, RequisitionUpdateRequest req) {
        Requisition e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Requisition 不存在");
        }
        e.setReqNo(req.reqNo());
        e.setDeptId(req.deptId());
        e.setPositionId(req.positionId());
        e.setHeadcount(req.headcount());
        e.setReason(req.reason());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /**
     * 提交审批（DRAFT→APPROVING）并发起 RCS_REQUISITION 流程（docs/19 §9 审批发起侧接入）。
     *
     * <p>守卫：仅 DRAFT 可提交；APPROVING（重复提交）/OPEN/CLOSED（终态）一律拒绝。
     * bizKey 约定 = 单据主键 id 十进制字符串（与 RecruitApprovalCallbackService 定位逻辑对齐）。
     * payload 仅含非敏感路由摘要（部门/岗位/编制数/理由）。</p>
     *
     * <p>fail-open：流程发起失败不回滚状态迁移，单据保留 APPROVING 返回成功
     * （同 attendance 域「仅落单不发起流程」降级范式）。rcu_requisition 无 proc_inst_id 列，
     * 发起成功仅记录日志。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public RequisitionResponse submit(Long id) {
        Requisition e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Requisition 不存在");
        }
        if (!STATUS_DRAFT.equals(e.getStatus())) {
            throw new BizException(ErrorCode.RCS_ERROR,
                    "仅草稿状态可提交审批，当前状态：" + e.getStatus());
        }
        e.setStatus(STATUS_APPROVING);
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        String instanceId = flowIntegration.start("RCS_REQUISITION", String.valueOf(e.getId()), buildPayload(e));
        if (instanceId != null) {
            log.info("RCS_REQUISITION 流程发起成功: reqId={}, instanceId={}", e.getId(), instanceId);
        }
        return toResponse(e);
    }

    /** 删除记录（事务）；不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Requisition 不存在");
        }
    }

    /**
     * 发布到官网（RCS-F-004 配套，事务）：仅 OPEN 状态可发布（DRAFT/APPROVING 未获批，
     * CLOSED 已关闭），published=1 后经 /api/v1/public/rcu/jobs 对外可见。
     */
    @Transactional(rollbackFor = Exception.class)
    public RequisitionResponse publish(Long id) {
        Requisition e = requireExists(id);
        if (!STATUS_OPEN.equals(e.getStatus())) {
            throw new BizException(ErrorCode.RCS_ERROR,
                    "仅 OPEN 状态可发布到官网，当前状态：" + e.getStatus());
        }
        e.setPublished(1);
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 从官网下架（事务）：任意状态可下架，published=0。 */
    @Transactional(rollbackFor = Exception.class)
    public RequisitionResponse unpublish(Long id) {
        Requisition e = requireExists(id);
        e.setPublished(0);
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    private Requisition requireExists(Long id) {
        Requisition e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Requisition 不存在");
        }
        return e;
    }

    /** 审批路由摘要（非敏感字段，flow 仅用于审批人解析，不落库）。 */
    private Map<String, Object> buildPayload(Requisition e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reqNo", e.getReqNo());
        payload.put("deptId", e.getDeptId());
        payload.put("positionId", e.getPositionId());
        payload.put("headcount", e.getHeadcount());
        payload.put("reason", e.getReason());
        return payload;
    }

    private RequisitionResponse toResponse(Requisition e) {
        return new RequisitionResponse(e.getId(), e.getReqNo(), e.getDeptId(), e.getPositionId(), e.getHeadcount(), e.getReason(), e.getStatus(), e.getPublished(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
