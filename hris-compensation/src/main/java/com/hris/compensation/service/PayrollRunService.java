package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunCreateRequest;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunResponse;
import com.hris.compensation.dto.PayrollRunDtos.PayrollRunUpdateRequest;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
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
 * PayrollRun 服务（CRUD）。
 *
 * <p>状态机守卫（§8.5-3）：批次进入 LOCK/PAID 后即为锁账终态，禁止 update/delete，
 * 「锁后仅调整通道」由此强制。状态取值见 cmp_payroll_run.status：
 * INIT/TRIAL/CALC/REVIEW/APPROVE/PAID/LOCK。</p>
 *
 * <p>状态迁移路径：submit 端点（INIT/TRIAL/CALC→REVIEW 并发起 CMP_PAYROLL 流程）与
 * flow 审批回调（REVIEW→APPROVE，见 CompApprovalCallbackService）及既有锁定/支付动作；
 * create/update 不接受 status 入参，防止状态机旁路。</p>
 *
 * <p>期间级守卫（CMP-F-020）：create/update/delete/submit 四路径在 run 级守卫之后
 * 检查 cmp_period_lock 的 ACTIVE 锁，期间已关账则拒绝变更。run 自身 LOCK 动作
 * 不联动期间锁（独立语义，见 PeriodLockService）。</p>
 */
@Service
public class PayrollRunService {

    private static final Logger log = LoggerFactory.getLogger(PayrollRunService.class);

    /** 状态：已锁定（锁账终态） */
    public static final String STATUS_LOCK = "LOCK";
    /** 状态：已支付（锁账终态） */
    public static final String STATUS_PAID = "PAID";
    /** 状态：审批中（流程已发起，等回调） */
    public static final String STATUS_REVIEW = "REVIEW";

    private final PayrollRunMapper mapper;
    private final PayrollDetailMapper detailMapper;
    private final FlowIntegration flowIntegration;
    private final PeriodLockService periodLockService;

    public PayrollRunService(PayrollRunMapper mapper, PayrollDetailMapper detailMapper,
                             FlowIntegration flowIntegration, PeriodLockService periodLockService) {
        this.mapper = mapper;
        this.detailMapper = detailMapper;
        this.flowIntegration = flowIntegration;
        this.periodLockService = periodLockService;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public PayrollRunResponse get(Long id) {
        PayrollRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<PayrollRunResponse> list(long page, long size) {
        IPage<PayrollRun> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayrollRunResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /**
     * 新建记录，落库后返回响应；@Transactional 保证写入原子性。状态固定 INIT（不接受请求体状态）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PayrollRunResponse create(PayrollRunCreateRequest req) {
        // 期间级守卫：该期间已关账则禁止新建批次
        periodLockService.ensurePeriodUnlocked(req.periodCode());
        PayrollRun e = new PayrollRun();
        e.setPeriodCode(req.periodCode());
        e.setPayGroupId(req.payGroupId());
        e.setStatus("INIT");
        e.setCalcBy(req.calcBy());
        e.setReviewBy(req.reviewBy());
        mapper.insert(e);
        return toResponse(e);
    }

    /**
     * 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。状态字段不在请求体内，不经此迁移。
     */
    @Transactional(rollbackFor = Exception.class)
    public PayrollRunResponse update(Long id, PayrollRunUpdateRequest req) {
        PayrollRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        // 当前已处于 LOCK/PAID：任何字段更新都被拒绝
        ensureMutable(e);
        // 期间级守卫：目标期间已关账则禁止变更（periodCode 可空，缺省沿用原值）
        periodLockService.ensurePeriodUnlocked(req.periodCode() != null ? req.periodCode() : e.getPeriodCode());
        e.setPeriodCode(req.periodCode());
        e.setPayGroupId(req.payGroupId());
        e.setCalcBy(req.calcBy());
        e.setReviewBy(req.reviewBy());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /**
     * 提交审批（INIT/TRIAL/CALC→REVIEW）并发起 CMP_PAYROLL 流程（docs/19 §9 审批发起侧接入）。
     *
     * <p>守卫：仅 INIT/TRIAL/CALC 可提交；REVIEW（重复提交）与 APPROVE/PAID/LOCK 拒绝。
     * bizKey 约定 = 批次主键 id 十进制字符串（与 CompApprovalCallbackService 定位逻辑对齐）。
     * payload 仅含非敏感摘要（期间/薪资组/人数），金额密文不外发。</p>
     *
     * <p>fail-open：流程发起失败不回滚状态迁移，批次保留 REVIEW 返回成功
     * （同 attendance 域「仅落单不发起流程」降级范式）。cmp_payroll_run 无 proc_inst_id 列，
     * 发起成功仅记录日志。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public PayrollRunResponse submit(Long id) {
        PayrollRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        String status = e.getStatus();
        if (STATUS_REVIEW.equals(status) || STATUS_PAID.equals(status) || STATUS_LOCK.equals(status)
                || "APPROVE".equals(status)) {
            throw new BizException(ErrorCode.CMP_ERROR,
                    "当前状态不可提交审批（" + status + "），仅 INIT/TRIAL/CALC 可提交");
        }
        // 期间级守卫：该期间已关账则禁止提交
        periodLockService.ensurePeriodUnlocked(e.getPeriodCode());
        e.setStatus(STATUS_REVIEW);
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        String instanceId = flowIntegration.start("CMP_PAYROLL", String.valueOf(e.getId()), buildPayload(e));
        if (instanceId != null) {
            log.info("CMP_PAYROLL 流程发起成功: runId={}, instanceId={}", e.getId(), instanceId);
        }
        return toResponse(e);
    }

    /** 按主键删除记录，不存在抛 NOT_FOUND；LOCK/PAID 终态拒绝删除。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PayrollRun e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
        ensureMutable(e);
        // 期间级守卫：该期间已关账则禁止删除
        periodLockService.ensurePeriodUnlocked(e.getPeriodCode());
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "PayrollRun 不存在");
        }
    }

    /** 锁账守卫：批次处于 LOCK/PAID 时禁止修改与删除，差异走薪酬调整通道。 */
    private void ensureMutable(PayrollRun e) {
        if (STATUS_LOCK.equals(e.getStatus()) || STATUS_PAID.equals(e.getStatus())) {
            throw new BizException(ErrorCode.CMP_ERROR,
                    "算薪批次已锁定或已支付（" + e.getStatus() + "），禁止修改或删除，差异请走调整通道");
        }
    }

    /** 审批路由摘要（非敏感字段；金额为密文列不进入 payload）。 */
    private Map<String, Object> buildPayload(PayrollRun e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("periodCode", e.getPeriodCode());
        payload.put("payGroupId", e.getPayGroupId());
        payload.put("employeeCount", detailMapper.selectCount(
                new LambdaQueryWrapper<com.hris.compensation.entity.PayrollDetail>()
                        .eq(com.hris.compensation.entity.PayrollDetail::getRunId, e.getId())));
        return payload;
    }

    private PayrollRunResponse toResponse(PayrollRun e) {
        return new PayrollRunResponse(e.getId(), e.getPeriodCode(), e.getPayGroupId(), e.getStatus(), e.getCalcBy(), e.getReviewBy(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
