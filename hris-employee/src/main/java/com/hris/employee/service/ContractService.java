package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.ContractDtos.ContractCreateRequest;
import com.hris.employee.dto.ContractDtos.ContractExpiringItem;
import com.hris.employee.dto.ContractDtos.ContractResponse;
import com.hris.employee.entity.Contract;
import com.hris.employee.entity.Employee;
import com.hris.employee.enums.ContractType;
import com.hris.employee.mapper.ContractMapper;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.util.CodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 合同台账（EMP-F-014 多法人合同、EMP-F-015 到期预警 60/30/7 多级）。
 */
@Service
public class ContractService {

    private final ContractMapper contractMapper;
    private final EmployeeMapper employeeMapper;

    public ContractService(ContractMapper contractMapper, EmployeeMapper employeeMapper) {
        this.contractMapper = contractMapper;
        this.employeeMapper = employeeMapper;
    }

    /** 台账查询：按员工（必填）+ 签订法人（可选）过滤，按 ID 倒序。 */
    public List<ContractResponse> list(Long empId, Long legalEntityId) {
        LambdaQueryWrapper<Contract> qw = new LambdaQueryWrapper<Contract>()
                .eq(Contract::getEmpId, empId);
        if (legalEntityId != null) {
            qw.eq(Contract::getLegalEntityId, legalEntityId);
        }
        qw.orderByDesc(Contract::getId);
        return contractMapper.selectList(qw).stream().map(this::toResponse).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractResponse create(ContractCreateRequest req) {
        if (employeeMapper.selectById(req.empId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        if (req.type() != null && ContractType.fromCode(req.type()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知合同类型: " + req.type());
        }
        Contract c = new Contract();
        c.setEmpId(req.empId());
        c.setContractNo(req.contractNo() == null || req.contractNo().isBlank()
                ? CodeUtil.next("ctr") : req.contractNo());
        c.setType(req.type());
        c.setStartDate(req.startDate());
        c.setEndDate(req.endDate());
        c.setProbationEnd(req.probationEnd());
        c.setLegalEntityId(req.legalEntityId());
        c.setRenewCnt(0);
        c.setFileId(req.fileId());
        c.setStatus(1);
        contractMapper.insert(c);
        return toResponse(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public ContractResponse renew(Long id, LocalDate newEndDate) {
        Contract c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "合同不存在");
        }
        if (c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException(ErrorCode.EMP_ERROR, "合同已终止，不可续签");
        }
        c.setEndDate(newEndDate);
        c.setRenewCnt((c.getRenewCnt() == null ? 0 : c.getRenewCnt()) + 1);
        contractMapper.updateById(c);
        return toResponse(c);
    }

    /** 到期预警：返回 0 < 剩余天数 <= days 的有效合同，按 7/30/60 分级。 */
    public List<ContractExpiringItem> expiring(int days) {
        int window = days <= 0 ? 60 : days;
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(window);
        List<Contract> list = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, 1)
                .isNotNull(Contract::getEndDate)
                .ge(Contract::getEndDate, today)
                .le(Contract::getEndDate, end)
                .orderByAsc(Contract::getEndDate));
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Employee> empMap = batchEmployees(list.stream().map(Contract::getEmpId).toList());
        return list.stream().map(c -> {
            Employee e = empMap.get(c.getEmpId());
            long remain = ChronoUnit.DAYS.between(today, c.getEndDate());
            return new ContractExpiringItem(c.getId(), c.getEmpId(),
                    e == null ? null : e.getEmpNo(),
                    e == null ? null : e.getName(),
                    c.getContractNo(), c.getEndDate(), remain, level(remain));
        }).toList();
    }

    private Map<Long, Employee> batchEmployees(List<Long> empIds) {
        if (empIds.isEmpty()) {
            return Map.of();
        }
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>().in(Employee::getId, empIds))
                .stream().collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a));
    }

    private String level(long remain) {
        if (remain <= 7) {
            return "LEVEL_7";
        }
        if (remain <= 30) {
            return "LEVEL_30";
        }
        return "LEVEL_60";
    }

    private ContractResponse toResponse(Contract c) {
        return new ContractResponse(c.getId(), c.getEmpId(), c.getContractNo(), c.getType(),
                c.getStartDate(), c.getEndDate(), c.getProbationEnd(), c.getLegalEntityId(),
                c.getRenewCnt(), c.getFileId(), c.getStatus());
    }
}
