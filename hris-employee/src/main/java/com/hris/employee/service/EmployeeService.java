package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.employee.dto.EmployeeBrief;
import com.hris.employee.dto.EmployeeDtos.EmployeeCreateRequest;
import com.hris.employee.dto.PreCreateRequest;
import com.hris.employee.dto.EmployeeDtos.EmployeeProfileResponse;
import com.hris.employee.dto.EmployeeDtos.EmployeeUpdateRequest;
import com.hris.employee.dto.EmployeeDtos.EmploymentInfo;
import com.hris.employee.dto.EmployeeDtos.RehireCheckResponse;
import com.hris.employee.dto.EmployeeDtos.RehireHistoryItem;
import com.hris.employee.dto.PageResult;
import com.hris.employee.dto.ProfileDtos.ProfileEducationDto;
import com.hris.employee.dto.ProfileDtos.ProfileEmergencyDto;
import com.hris.employee.dto.ProfileDtos.ProfileFamilyDto;
import com.hris.employee.dto.ProfileDtos.ProfileWorkDto;
import com.hris.employee.entity.ChangeAudit;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.enums.EmployeeStatus;
import com.hris.employee.mapper.ChangeAuditMapper;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.EmploymentMapper;
import com.hris.employee.util.MaskUtil;
import com.hris.common.security.AuthContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 员工档案（EMP-F-001 建档/详情/更新、EMP-F-016 黑名单、EMP-F-022 花名册、内部档案简报）。 */
@Service
public class EmployeeService {

    /**
     * 字段组读取权限码（EMP-F-001 分签维护：无对应权限码时该分组脱敏返回空集合）。
     * 常量命名须符合 scripts/seed_rbac_from_source.py 的 PERM 常量扫描规则，授予 admin。
     */
    public static final String PERM_PROFILE_READ_EDUCATION = "emp:profile:read-education";
    public static final String PERM_PROFILE_READ_WORK = "emp:profile:read-work";
    public static final String PERM_PROFILE_READ_FAMILY = "emp:profile:read-family";
    public static final String PERM_PROFILE_READ_EMERGENCY = "emp:profile:read-emergency";

    private final EmployeeMapper employeeMapper;
    private final EmploymentMapper employmentMapper;
    private final ChangeAuditMapper changeAuditMapper;
    private final EmpNoService empNoService;
    private final EmploymentService employmentService;
    private final ProfileService profileService;
    private final EmployeeStateMachine stateMachine;

    public EmployeeService(EmployeeMapper employeeMapper, EmploymentMapper employmentMapper,
                           ChangeAuditMapper changeAuditMapper, EmpNoService empNoService,
                           EmploymentService employmentService, ProfileService profileService, EmployeeStateMachine stateMachine) {
        this.employeeMapper = employeeMapper;
        this.employmentMapper = employmentMapper;
        this.changeAuditMapper = changeAuditMapper;
        this.empNoService = empNoService;
        this.employmentService = employmentService;
        this.profileService = profileService;
        this.stateMachine = stateMachine;
    }

    /** 建档（状态=待入职）。 */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileResponse create(EmployeeCreateRequest req) {
        Employee e = new Employee();
        e.setEmpNo(empNoService.next(req.empNoPrefix()));
        e.setName(req.name());
        e.setCertType(req.certType() == null || req.certType().isBlank() ? "ID_CARD" : req.certType());
        e.setCertNoCipher(req.certNo());
        e.setCertNoHash(hashCertNo(req.certNo()));
        e.setCertNoMask(MaskUtil.maskCertNo(req.certNo()));
        e.setMobileCipher(req.mobile());
        e.setMobileMask(MaskUtil.maskMobile(req.mobile()));
        e.setEmail(req.email());
        e.setGender(req.gender());
        e.setBirthDate(req.birthDate());
        e.setStatus(EmployeeStatus.PENDING.getCode());
        e.setBlacklisted(0);
        employeeMapper.insert(e);
        profileService.replace(e.getId(), req.educations(), req.works(), req.families(), req.emergencies());
        return get(e.getId());
    }

    /** 花名册多维筛选 + 分页（EMP-F-022）。 */
    public PageResult<EmployeeBrief> roster(String name, String empNo, Integer status, Long deptId,
                                            Long positionId, String hireType, long page, long size) {
        List<Long> empIds = resolveEmpIdsByEmployment(deptId, positionId, hireType);
        if (empIds != null && empIds.isEmpty()) {
            return new PageResult<>(List.of(), page, size, 0);
        }
        Page<Employee> p = new Page<>(page, size);
        LambdaQueryWrapper<Employee> qw = new LambdaQueryWrapper<>();
        if (empIds != null) {
            qw.in(Employee::getId, empIds);
        }
        if (name != null && !name.isBlank()) {
            qw.like(Employee::getName, name);
        }
        if (empNo != null && !empNo.isBlank()) {
            qw.eq(Employee::getEmpNo, empNo);
        }
        if (status != null) {
            qw.eq(Employee::getStatus, status);
        }
        qw.orderByDesc(Employee::getId);
        Page<Employee> result = employeeMapper.selectPage(p, qw);

        Map<Long, Employment> empMap = batchCurrentEmployments(result.getRecords());
        List<EmployeeBrief> briefs = result.getRecords().stream()
                .map(e -> toBrief(e, empMap.get(e.getId())))
                .toList();
        return new PageResult<>(briefs, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 档案详情（默认脱敏）。 */
    public EmployeeProfileResponse get(Long id) {
        Employee e = require(id);
        return toProfile(e, false);
    }

    /** 档案更新（敏感字段变更落 diff 审计 EMP-F-020）。 */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileResponse update(Long id, EmployeeUpdateRequest req) {
        Employee e = require(id);
        applySensitiveChange(e, "cert_no", e.getCertNoCipher(), req.certNo());
        applySensitiveChange(e, "mobile", e.getMobileCipher(), req.mobile());
        if (req.name() != null && !req.name().isBlank()) {
            e.setName(req.name());
        }
        if (req.email() != null) {
            e.setEmail(req.email());
        }
        if (req.gender() != null) {
            e.setGender(req.gender());
        }
        if (req.birthDate() != null) {
            e.setBirthDate(req.birthDate());
        }
        if (req.certType() != null && !req.certType().isBlank()) {
            e.setCertType(req.certType());
        }
        employeeMapper.updateById(e);
        profileService.replace(id, req.educations(), req.works(), req.families(), req.emergencies());
        return get(id);
    }

    /** 教育经历分签独立整体替换（EMP-F-001：null 保留旧数据，空列表清空该域）。 */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileResponse updateEducations(Long id, List<ProfileEducationDto> educations) {
        require(id);
        profileService.replaceEducations(id, educations);
        return get(id);
    }

    /** 工作经历分签独立整体替换。 */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileResponse updateWorks(Long id, List<ProfileWorkDto> works) {
        require(id);
        profileService.replaceWorks(id, works);
        return get(id);
    }

    /** 家庭成员分签独立整体替换。 */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileResponse updateFamilies(Long id, List<ProfileFamilyDto> families) {
        require(id);
        profileService.replaceFamilies(id, families);
        return get(id);
    }

    /** 紧急联系人分签独立整体替换。 */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileResponse updateEmergencies(Long id, List<ProfileEmergencyDto> emergencies) {
        require(id);
        profileService.replaceEmergencies(id, emergencies);
        return get(id);
    }

    /**
     * 再入职识别（EMP-F-029）：按证件号查历史任职记录，供录用/建档前查重。
     * <p>返回全部命中记录而不只是已离职的，因为三种情况处置不同：
     * 命中已离职 -> 走二次雇佣（工龄是否连续由业务决定）；
     * 命中在职 -> 属重复建档，应直接使用现有档案；
     * 命中黑名单 -> 禁止录用。判断交给调用方，本方法不做裁决。</p>
     * <p>实现说明：优先按 cert_no_hash（SHA-256 小写 hex）等值查询，该值不随加密改造失效；
     * cert_no_hash 为空的存量行回退 cert_no_cipher 等值匹配（docs/19 §8.7-3）。</p>
     */
    public RehireCheckResponse rehireCheck(String certNo) {
        if (certNo == null || certNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "证件号不能为空");
        }
        String plain = certNo.trim();
        LambdaQueryWrapper<Employee> qw = new LambdaQueryWrapper<>();
        qw.eq(Employee::getCertNoHash, hashCertNo(plain))
                .or(nested -> nested.isNull(Employee::getCertNoHash).eq(Employee::getCertNoCipher, plain));
        qw.orderByDesc(Employee::getId);
        List<Employee> hits = employeeMapper.selectList(qw);

        List<RehireHistoryItem> items = hits.stream().map(e -> {
            EmployeeStatus st = EmployeeStatus.fromCode(e.getStatus());
            return new RehireHistoryItem(e.getId(), e.getEmpNo(), e.getName(),
                    e.getStatus(), st == null ? "未知状态" : st.getLabel(), e.getBlacklisted());
        }).toList();
        return new RehireCheckResponse(!items.isEmpty(), items.size(), items);
    }

    /** 加入黑名单（终态标记 EMP-F-016）。 */
    @Transactional(rollbackFor = Exception.class)
    public void blacklist(Long id, String reason) {
        Employee e = require(id);
        if (e.getBlacklisted() != null && e.getBlacklisted() == 1) {
            throw new BizException(ErrorCode.EMP_ERROR, "员工已在黑名单中");
        }
        e.setBlacklisted(1);
        stateMachine.transition(e, EmployeeStatus.BLACKLISTED, reason == null || reason.isBlank() ? "加入黑名单" : reason);
    }

    /** 候选人预建档（内部端点调用，状态=待入职）。复用 create 建档逻辑，仅注入最小字段。 */
    public Long preCreate(PreCreateRequest req) {
        EmployeeCreateRequest create = new EmployeeCreateRequest(
                req.name(), req.certType(), req.certNo(), req.mobile(), req.email(),
                req.gender(), req.birthDate(), req.empNoPrefix(),
                List.of(), List.of(), List.of(), List.of());
        EmployeeProfileResponse profile = create(create);
        return profile.id();
    }

    /** 内部档案简报（供 org 汇报线/rag 用，docs/07：GET /internal/emp/{id}/profile-brief）。 */
    public Map<String, Object> profileBrief(Long empId) {
        Employee e = employeeMapper.selectById(empId);
        if (e == null) {
            return null;
        }
        Employment emp = employmentService.current(empId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("empId", e.getId());
        m.put("name", e.getName());
        m.put("empNo", e.getEmpNo());
        m.put("status", e.getStatus());
        m.put("tenantId", e.getTenantId());
        m.put("deptId", emp == null ? null : emp.getDeptId());
        m.put("positionId", emp == null ? null : emp.getPositionId());
        m.put("supervisorEmpId", emp == null ? null : emp.getSupervisorEmpId());
        return m;
    }

    /** 证件号哈希：SHA-256 小写十六进制（等值查重键，生产者路径唯一化）。 */
    private String hashCertNo(String certNo) {
        if (certNo == null || certNo.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(certNo.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Employee require(Long id) {
        Employee e = employeeMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return e;
    }

    /** 敏感字段 diff 审计 + 更新密文/掩码列（MVP 密文列存明文，见实体注释）。 */
    private void applySensitiveChange(Employee e, String field, String currentCipher, String newPlain) {
        if (newPlain == null || newPlain.isBlank()) {
            return;
        }
        if (newPlain.equals(currentCipher)) {
            return;
        }
        ChangeAudit audit = new ChangeAudit();
        audit.setEmpId(e.getId());
        audit.setFieldName(field);
        audit.setOldValue(field.equals("cert_no") ? e.getCertNoMask() : e.getMobileMask());
        if ("cert_no".equals(field)) {
            audit.setNewValue(MaskUtil.maskCertNo(newPlain));
            e.setCertNoCipher(newPlain);
            e.setCertNoHash(hashCertNo(newPlain));
            e.setCertNoMask(MaskUtil.maskCertNo(newPlain));
        } else {
            audit.setNewValue(MaskUtil.maskMobile(newPlain));
            e.setMobileCipher(newPlain);
            e.setMobileMask(MaskUtil.maskMobile(newPlain));
        }
        changeAuditMapper.insert(audit);
    }

    private List<Long> resolveEmpIdsByEmployment(Long deptId, Long positionId, String hireType) {
        if (deptId == null && positionId == null && (hireType == null || hireType.isBlank())) {
            return null;
        }
        LambdaQueryWrapper<Employment> ew = new LambdaQueryWrapper<Employment>()
                .eq(Employment::getStatus, 1)
                .isNull(Employment::getEffectiveTo);
        if (deptId != null) {
            ew.eq(Employment::getDeptId, deptId);
        }
        if (positionId != null) {
            ew.eq(Employment::getPositionId, positionId);
        }
        if (hireType != null && !hireType.isBlank()) {
            ew.eq(Employment::getHireType, hireType);
        }
        return employmentMapper.selectList(ew).stream().map(Employment::getEmpId).toList();
    }

    private Map<Long, Employment> batchCurrentEmployments(List<Employee> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(Employee::getId).toList();
        List<Employment> list = employmentMapper.selectList(new LambdaQueryWrapper<Employment>()
                .in(Employment::getEmpId, ids)
                .eq(Employment::getStatus, 1)
                .isNull(Employment::getEffectiveTo));
        return list.stream().collect(Collectors.toMap(Employment::getEmpId, Function.identity(), (a, b) -> a));
    }

    private EmployeeBrief toBrief(Employee e, Employment emp) {
        return new EmployeeBrief(e.getId(), e.getEmpNo(), e.getName(), e.getGender(),
                e.getStatus(), statusLabel(e.getStatus()), e.getBlacklisted(),
                e.getCertNoMask(), e.getMobileMask(), e.getEmail(),
                emp == null ? null : emp.getDeptId(),
                emp == null ? null : emp.getPositionId(),
                emp == null ? null : emp.getHireType(),
                emp == null ? null : emp.getSupervisorEmpId());
    }

    private EmployeeProfileResponse toProfile(Employee e, boolean sensitive) {
        Employment emp = employmentService.current(e.getId());
        ProfileService.ProfileSnapshot ps = profileService.load(e.getId());
        return new EmployeeProfileResponse(
                e.getId(), e.getEmpNo(), e.getName(), e.getGender(), e.getBirthDate(), e.getEmail(),
                e.getStatus(), statusLabel(e.getStatus()), e.getBlacklisted(),
                e.getCertNoMask(), e.getMobileMask(),
                sensitive ? e.getCertNoCipher() : null,
                sensitive ? e.getMobileCipher() : null,
                emp == null ? null : new EmploymentInfo(emp.getLegalEntityId(), emp.getDeptId(),
                        emp.getPositionId(), emp.getGradeId(), emp.getHireType(),
                        emp.getSupervisorEmpId(), emp.getEffectiveFrom()),
                visibleGroup(ps.educations(), PERM_PROFILE_READ_EDUCATION),
                visibleGroup(ps.works(), PERM_PROFILE_READ_WORK),
                visibleGroup(ps.families(), PERM_PROFILE_READ_FAMILY),
                visibleGroup(ps.emergencies(), PERM_PROFILE_READ_EMERGENCY));
    }

    /**
     * 字段组权限过滤（EMP-F-001 字段分组权限）：当前用户无该组权限码时整组返回空集合（脱敏兜底）。
     * 权限上下文缺失（内部调度/系统调用）同样视为无权限，宁可不发不可多发。
     */
    private <T> List<T> visibleGroup(List<T> list, String perm) {
        return hasPerm(perm) ? list : List.of();
    }

    private boolean hasPerm(String perm) {
        java.util.Set<String> perms = AuthContextHolder.getPerms();
        return perms != null && perms.contains(perm);
    }

    private String statusLabel(Integer code) {
        EmployeeStatus s = EmployeeStatus.fromCode(code);
        return s == null ? null : s.getLabel();
    }
}
