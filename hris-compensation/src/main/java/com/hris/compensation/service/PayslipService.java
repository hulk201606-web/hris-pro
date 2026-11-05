package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PayslipDtos.PayslipCreateRequest;
import com.hris.compensation.dto.PayslipDtos.PayslipMeResponse;
import com.hris.compensation.dto.PayslipDtos.PayslipResponse;
import com.hris.compensation.dto.PayslipDtos.PayslipUpdateRequest;
import com.hris.compensation.entity.PayrollDetail;
import com.hris.compensation.entity.PayrollRun;
import com.hris.compensation.entity.Payslip;
import com.hris.compensation.mapper.PayrollDetailMapper;
import com.hris.compensation.mapper.PayrollRunMapper;
import com.hris.compensation.mapper.PayslipMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Payslip 服务（CRUD）。
 */
@Service
public class PayslipService {

    /** 自助工资条列表上限：工资条按月产生，100 条足够覆盖个人视图。 */
    private static final long SELF_LIST_LIMIT = 100;

    private final PayslipMapper mapper;
    private final PayrollRunMapper runMapper;
    private final PayrollDetailMapper detailMapper;

    public PayslipService(PayslipMapper mapper, PayrollRunMapper runMapper, PayrollDetailMapper detailMapper) {
        this.mapper = mapper;
        this.runMapper = runMapper;
        this.detailMapper = detailMapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public PayslipResponse get(Long id) {
        Payslip e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payslip 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<PayslipResponse> list(long page, long size) {
        IPage<Payslip> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayslipResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public PayslipResponse create(PayslipCreateRequest req) {
        Payslip e = new Payslip();
        e.setRunId(req.runId());
        e.setEmpId(req.empId());
        e.setNetPayCipher(req.netPayCipher());
        e.setNetPayMask(req.netPayMask());
        e.setSendStatus(req.sendStatus());
        e.setSentAt(req.sentAt());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public PayslipResponse update(Long id, PayslipUpdateRequest req) {
        Payslip e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payslip 不存在");
        }
        e.setRunId(req.runId());
        e.setEmpId(req.empId());
        e.setNetPayCipher(req.netPayCipher());
        e.setNetPayMask(req.netPayMask());
        e.setSendStatus(req.sendStatus());
        e.setSentAt(req.sentAt());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payslip 不存在");
        }
    }

    /** 员工自助工资条列表：按当前员工过滤，仅掩码字段；补充批次周期与应发掩码。 */
    public List<PayslipMeResponse> listMe(Long empId) {
        List<Payslip> payslips = mapper.selectList(new LambdaQueryWrapper<Payslip>()
                .eq(Payslip::getEmpId, empId)
                .orderByDesc(Payslip::getId)
                .last("LIMIT " + SELF_LIST_LIMIT));
        if (payslips.isEmpty()) {
            return List.of();
        }
        List<Long> runIds = payslips.stream()
                .map(Payslip::getRunId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> periodByRun = loadPeriods(runIds);
        Map<Long, String> grossByRun = loadGrossMasks(empId, runIds);
        return payslips.stream()
                .map(e -> new PayslipMeResponse(e.getId(), e.getRunId(),
                        periodByRun.get(e.getRunId()), grossByRun.get(e.getRunId()),
                        e.getNetPayMask(), e.getSendStatus(), e.getSentAt()))
                .toList();
    }

    private Map<Long, String> loadPeriods(List<Long> runIds) {
        if (runIds.isEmpty()) {
            return Map.of();
        }
        return runMapper.selectBatchIds(runIds).stream()
                .filter(r -> r.getPeriodCode() != null)
                .collect(Collectors.toMap(PayrollRun::getId, PayrollRun::getPeriodCode, (a, b) -> a));
    }

    private Map<Long, String> loadGrossMasks(Long empId, List<Long> runIds) {
        if (runIds.isEmpty()) {
            return Map.of();
        }
        return detailMapper.selectList(new LambdaQueryWrapper<PayrollDetail>()
                        .eq(PayrollDetail::getEmpId, empId)
                        .in(PayrollDetail::getRunId, runIds)).stream()
                .filter(d -> d.getAmountMask() != null)
                .collect(Collectors.toMap(PayrollDetail::getRunId, PayrollDetail::getAmountMask, (a, b) -> a));
    }

    private PayslipResponse toResponse(Payslip e) {
        // 密域下沉：netPayCipher 不随通用响应暴露，仅掩码；密文走 SalarySecretService
        return new PayslipResponse(e.getId(), e.getRunId(), e.getEmpId(), e.getNetPayMask(), e.getSendStatus(), e.getSentAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
