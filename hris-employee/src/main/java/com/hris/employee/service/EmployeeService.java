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
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.enums.EmployeeStatus;
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
    private final EmpChangeAuditService changeAuditService;
    private final EmpNoService empNoService;
    private final EmploymentService employmentService;
    private final ProfileService profileService;
    private final EmployeeStateMachine stateMachine;
    /** 花名册查询/档案组装/变更落账（拆分自本类，构造器内实例化，保持外部构造器签名 7 参不变）。 */
    private final EmployeeRosterQuery rosterQuery;
    private final EmployeeProfileAssembler assembler;
    private final EmployeeProfileChangeApplier changeApplier;

    public EmployeeService(EmployeeMapper employeeMapper, EmploymentMapper employmentMapper,
                           EmpChangeAuditService changeAuditService, EmpNoService empNoService,
                           EmploymentService employmentService, ProfileService profileService, EmployeeStateMachine stateMachine) {
        this.employeeMapper = employeeMapper;
        this.employmentMapper = employmentMapper;
        this.changeAuditService = changeAuditService;
        this.empNoService = empNoService;
        this.employmentService = employmentService;
        this.profileService = profileService;
        this.stateMachine = stateMachine;
        this.rosterQuery = new EmployeeRosterQuery(employeeMapper, employmentMapper);
        this.assembler = new EmployeeProfileAssembler(employmentService, profileService);
        this.changeApplier = new EmployeeProfileChangeApplier(employeeMapper, employmentMapper,
                changeAuditService, employmentService);
    }

    /** 建档（状态=待入职）。 */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileResponse create(EmployeeCreateRequest req) {
        Employee e = new Employee();
        e.setEmpNo(empNoService.next(req.empNoPrefix()));
        e.setName(req.name());
        e.setCertType(req.certType() == null || req.certType().isBlank() ? "ID_CARD" : req.certType());
        e.setCertNoCipher(req.certNo());
        e.setCertNoHash(EmployeeIdentitySupport.hashCertNo(req.certNo()));
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
        changeAuditService.record(e.getId(), "CREATE", null, EmployeeIdentitySupport.snapshot(e), "建档");
        return get(e.getId());
    }

    /** 花名册多维筛选 + 分页（EMP-F-022），实现见 {@link EmployeeRosterQuery}。 */
    public PageResult<EmployeeBrief> roster(String name, String empNo, Integer status, Long deptId,
                                            Long positionId, String hireType, long page, long size) {
        return rosterQuery.roster(name, empNo, status, deptId, positionId, hireType, page, size);
    }

    /** 关键信息变更审批通过后落账（EMP_PROFILE_CHG 回调），实现见 {@link EmployeeProfileChangeApplier}。 */
    @Transactional(rollbackFor = Exception.class)
    public void applyProfileChange(Long empId, Map<String, Object> changes, String reason) {
        changeApplier.apply(empId, changes, reason);
    }

    /** 取员工实体，不存在抛 NOT_FOUND。 */
    private Employee require(Long id) {
        return EmployeeIdentitySupport.require(employeeMapper, id);
    }

    /** 档案详情（默认脱敏）。 */
    public EmployeeProfileResponse get(Long id) {
        Employee e = require(id);
        return assembler.toProfile(e, false);
    }

    /** 档案更新（敏感字段变更落 diff 审计 EMP-F-020）。 */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeProfileResponse update(Long id, EmployeeUpdateRequest req) {
        Employee e = require(id);
        Map<String, Object> before = EmployeeIdentitySupport.snapshot(e);
        EmployeeIdentitySupport.applySensitiveChange(e, "cert_no", e.getCertNoCipher(), req.certNo());
        EmployeeIdentitySupport.applySensitiveChange(e, "mobile", e.getMobileCipher(), req.mobile());
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
        changeAuditService.record(id, "UPDATE", before, EmployeeIdentitySupport.snapshot(e), null);
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
        qw.eq(Employee::getCertNoHash, EmployeeIdentitySupport.hashCertNo(plain))
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

}
