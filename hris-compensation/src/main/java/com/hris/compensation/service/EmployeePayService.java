package com.hris.compensation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.compensation.dto.EmployeePayDtos.EmployeePayCreateRequest;
import com.hris.compensation.dto.EmployeePayDtos.EmployeePayResponse;
import com.hris.compensation.dto.EmployeePayDtos.EmployeePayUpdateRequest;
import com.hris.compensation.entity.EmployeePay;
import com.hris.compensation.mapper.EmployeePayMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * EmployeePay 服务（CRUD）。
 */
@Service
public class EmployeePayService {

    private final EmployeePayMapper mapper;

    public EmployeePayService(EmployeePayMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public EmployeePayResponse get(Long id) {
        EmployeePay e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "EmployeePay 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<EmployeePayResponse> list(long page, long size) {
        IPage<EmployeePay> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<EmployeePayResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public EmployeePayResponse create(EmployeePayCreateRequest req) {
        EmployeePay e = new EmployeePay();
        e.setEmpId(req.empId());
        e.setPayGroupId(req.payGroupId());
        e.setPayGradeId(req.payGradeId());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setSalaryAmountCipher(req.salaryAmountCipher());
        e.setSalaryAmountMask(req.salaryAmountMask());
        e.setBankAccountCipher(req.bankAccountCipher());
        e.setBankAccountMask(req.bankAccountMask());
        mapper.insert(e);
        return toResponse(e);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public EmployeePayResponse update(Long id, EmployeePayUpdateRequest req) {
        EmployeePay e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "EmployeePay 不存在");
        }
        e.setEmpId(req.empId());
        e.setPayGroupId(req.payGroupId());
        e.setPayGradeId(req.payGradeId());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setSalaryAmountCipher(req.salaryAmountCipher());
        e.setSalaryAmountMask(req.salaryAmountMask());
        e.setBankAccountCipher(req.bankAccountCipher());
        e.setBankAccountMask(req.bankAccountMask());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.CMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }
    /** 按主键删除记录，不存在抛 NOT_FOUND。 */

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "EmployeePay 不存在");
        }
    }

    private EmployeePayResponse toResponse(EmployeePay e) {
        // 密域下沉：salaryAmountCipher/bankAccountCipher 不随通用响应暴露，仅掩码；密文走 SalarySecretService
        return new EmployeePayResponse(e.getId(), e.getEmpId(), e.getPayGroupId(), e.getPayGradeId(), e.getEffectiveFrom(), e.getEffectiveTo(), e.getSalaryAmountMask(), e.getBankAccountMask(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
