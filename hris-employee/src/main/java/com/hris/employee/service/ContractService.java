package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.ContractDtos.ContractCreateRequest;
import com.hris.employee.dto.ContractDtos.ContractExpiringItem;
import com.hris.employee.dto.ContractDtos.ContractResponse;
import com.hris.employee.dto.ContractTermDtos.ContractTermResponse;
import com.hris.employee.dto.ContractTermDtos.ContractTermUpsertRequest;
import com.hris.employee.entity.Contract;
import com.hris.employee.entity.ContractTerm;
import com.hris.employee.entity.Employee;
import com.hris.employee.enums.ContractType;
import com.hris.employee.mapper.ContractMapper;
import com.hris.employee.mapper.ContractTermMapper;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.util.CodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 合同台账（EMP-F-014 多法人合同、EMP-F-015 到期预警 60/30/7 多级）。
 *
 * <p>条款明细（EMP-F-014 深化）：emp_contract_term 的 CRUD 挂在合同资源之下，
 * termId 必须归属路径中的 contractId，否则 NOT_FOUND（防跨合同越权）。
 * 注：合同台账当前无删除端点（仅登记/续签），故无级联清理场景。</p>
 */
@Service
public class ContractService {

    /** 允许的条款类型 */
    private static final Set<String> TERM_TYPES = Set.of(
            "PROBATION", "SALARY", "CONFIDENTIALITY", "NON_COMPETE", "CUSTOM");

    private final ContractMapper contractMapper;
    private final ContractTermMapper termMapper;
    private final EmployeeMapper employeeMapper;

    public ContractService(ContractMapper contractMapper, ContractTermMapper termMapper,
                           EmployeeMapper employeeMapper) {
        this.contractMapper = contractMapper;
        this.termMapper = termMapper;
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

    /* ---------- 条款明细（EMP-F-014 深化） ---------- */

    /** 查询合同条款列表，按生效日倒序。 */
    public List<ContractTermResponse> listTerms(Long contractId) {
        requireContract(contractId);
        return termMapper.selectList(new LambdaQueryWrapper<ContractTerm>()
                        .eq(ContractTerm::getContractId, contractId)
                        .orderByDesc(ContractTerm::getEffectiveFrom))
                .stream().map(this::toTermResponse).toList();
    }

    /** 新增条款：合同须存在，term_type 合法，effective_to 晚于 effective_from。 */
    @Transactional(rollbackFor = Exception.class)
    public ContractTermResponse createTerm(Long contractId, ContractTermUpsertRequest req) {
        requireContract(contractId);
        validateTerm(req);
        ContractTerm t = new ContractTerm();
        t.setContractId(contractId);
        applyTerm(t, req);
        termMapper.insert(t);
        return toTermResponse(t);
    }

    /** 更新条款：termId 不属于该合同 → NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public ContractTermResponse updateTerm(Long contractId, Long termId, ContractTermUpsertRequest req) {
        requireContract(contractId);
        ContractTerm t = requireTerm(contractId, termId);
        validateTerm(req);
        applyTerm(t, req);
        if (termMapper.updateById(t) == 0) {
            throw new BizException(ErrorCode.EMP_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toTermResponse(t);
    }

    /** 删除条款（逻辑删除，随 BaseEntity @TableLogic），termId 不属于该合同 → NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTerm(Long contractId, Long termId) {
        requireContract(contractId);
        ContractTerm t = requireTerm(contractId, termId);
        if (termMapper.deleteById(t.getId()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "合同条款不存在");
        }
    }

    private void requireContract(Long contractId) {
        if (contractMapper.selectById(contractId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "合同不存在");
        }
    }

    private ContractTerm requireTerm(Long contractId, Long termId) {
        ContractTerm t = termMapper.selectById(termId);
        if (t == null || !contractId.equals(t.getContractId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "合同条款不存在");
        }
        return t;
    }

    private void validateTerm(ContractTermUpsertRequest req) {
        if (!TERM_TYPES.contains(req.termType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知条款类型: " + req.termType());
        }
        if (req.effectiveTo() != null && !req.effectiveTo().isAfter(req.effectiveFrom())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "effectiveTo 必须晚于 effectiveFrom");
        }
    }

    private void applyTerm(ContractTerm t, ContractTermUpsertRequest req) {
        t.setTermType(req.termType());
        t.setContent(req.content());
        t.setEffectiveFrom(req.effectiveFrom());
        t.setEffectiveTo(req.effectiveTo());
    }

    private ContractTermResponse toTermResponse(ContractTerm t) {
        return new ContractTermResponse(t.getId(), t.getContractId(), t.getTermType(),
                t.getContent(), t.getEffectiveFrom(), t.getEffectiveTo());
    }
}
