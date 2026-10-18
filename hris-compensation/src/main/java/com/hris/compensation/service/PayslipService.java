package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.PayslipDtos.PayslipCreateRequest;
import com.hris.compensation.dto.PayslipDtos.PayslipResponse;
import com.hris.compensation.dto.PayslipDtos.PayslipUpdateRequest;
import com.hris.compensation.entity.Payslip;
import com.hris.compensation.mapper.PayslipMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Payslip 服务（CRUD）。
 */
@Service
public class PayslipService {

    private final PayslipMapper mapper;

    public PayslipService(PayslipMapper mapper) {
        this.mapper = mapper;
    }

    public PayslipResponse get(Long id) {
        Payslip e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payslip 不存在");
        }
        return toResponse(e);
    }

    public PageResult<PayslipResponse> list(long page, long size) {
        IPage<Payslip> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<PayslipResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

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

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Payslip 不存在");
        }
    }

    private PayslipResponse toResponse(Payslip e) {
        // 密域下沉：netPayCipher 不随通用响应暴露，仅掩码；密文走 SalarySecretService
        return new PayslipResponse(e.getId(), e.getRunId(), e.getEmpId(), e.getNetPayMask(), e.getSendStatus(), e.getSentAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
