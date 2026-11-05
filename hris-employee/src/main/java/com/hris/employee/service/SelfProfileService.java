package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.employee.dto.SelfDtos.CriticalChangeRequest;
import com.hris.employee.dto.SelfDtos.CriticalChangeResponse;
import com.hris.employee.dto.SelfDtos.SelfProfileResponse;
import com.hris.employee.dto.SelfDtos.SelfProfileUpdateRequest;
import com.hris.employee.entity.EmpProfileChange;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.Employment;
import com.hris.employee.entity.ProfileEmergency;
import com.hris.employee.integration.FlowIntegration;
import com.hris.employee.mapper.EmpProfileChangeMapper;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.ProfileEmergencyMapper;
import com.hris.employee.util.CodeUtil;
import com.hris.employee.util.JsonUtil;
import com.hris.employee.util.MaskUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工自助信息变更（SELF-F-002 / EMP-F-021）。
 *
 * <p>非关键字段（姓名/手机/邮箱/地址/紧急联系人）直改生效并落 diff 审计；
 * 关键字段（证件号/证件类型/银行卡/雇佣类型）一律拒绝，返回 PARAM_INVALID 并提示
 * 流程模板键 {@value #TEMPLATE_KEY}，由前端引导发起审批。审批通过后 flow 回调
 * {@code /internal/emp/approval-callback}，按 bizKey 找回申请单并落账（幂等）。</p>
 */
@Service
public class SelfProfileService {

    /** 关键信息变更流程模板键（flow 预置模板 EMP_PROFILE_CHG）。 */
    public static final String TEMPLATE_KEY = "EMP_PROFILE_CHG";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPLIED = "APPLIED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final EmployeeMapper employeeMapper;
    private final ProfileEmergencyMapper emergencyMapper;
    private final EmpProfileChangeMapper changeRequestMapper;
    private final EmpChangeAuditService auditService;
    private final EmployeeService employeeService;
    private final EmploymentService employmentService;
    private final FlowIntegration flowIntegration;

    public SelfProfileService(EmployeeMapper employeeMapper, ProfileEmergencyMapper emergencyMapper,
                              EmpProfileChangeMapper changeRequestMapper,
                              EmpChangeAuditService auditService, EmployeeService employeeService,
                              EmploymentService employmentService, FlowIntegration flowIntegration) {
        this.employeeMapper = employeeMapper;
        this.emergencyMapper = emergencyMapper;
        this.changeRequestMapper = changeRequestMapper;
        this.auditService = auditService;
        this.employeeService = employeeService;
        this.employmentService = employmentService;
        this.flowIntegration = flowIntegration;
    }

    /** 我的信息（证件/手机仅掩码）。 */
    public SelfProfileResponse profile(Long empId) {
        Employee e = require(empId);
        Employment emp = currentEmployment(empId);
        ProfileEmergency emergency = firstEmergency(empId);
        return new SelfProfileResponse(e.getId(), e.getEmpNo(), e.getName(), e.getCertNoMask(),
                e.getMobileMask(), e.getEmail(), e.getAddress(),
                emergency == null ? null : emergency.getName(),
                emergency == null ? null : emergency.getPhone(),
                emp == null ? null : emp.getDeptId(),
                emp == null ? null : emp.getPositionId(),
                emp == null ? null : emp.getHireType());
    }

    /**
     * 自助变更：关键字段出现非空值即整体拒绝（不做部分写入，避免前端误以为部分成功）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SelfProfileResponse update(Long empId, SelfProfileUpdateRequest req) {
        rejectCritical(req);
        Employee e = require(empId);
        Map<String, Object> before = snapshot(e);
        ProfileEmergency oldEmergency = firstEmergency(empId);
        if (oldEmergency != null) {
            before.put("emergencyContact", oldEmergency.getName());
            before.put("emergencyPhone", oldEmergency.getPhone());
        }
        if (req.name() != null && !req.name().isBlank()) {
            e.setName(req.name().trim());
        }
        if (req.mobile() != null && !req.mobile().isBlank()) {
            // 走实体密文列 TypeHandler，不绕过加密路径
            e.setMobileCipher(req.mobile().trim());
            e.setMobileMask(MaskUtil.maskMobile(req.mobile().trim()));
        }
        if (req.email() != null) {
            e.setEmail(req.email().trim());
        }
        if (req.address() != null) {
            e.setAddress(req.address().trim());
        }
        employeeMapper.updateById(e);
        Map<String, Object> after = snapshot(e);
        ProfileEmergency emergency = updateEmergency(empId, req.emergencyContact(), req.emergencyPhone());
        after.put("emergencyContact", emergency == null ? null : emergency.getName());
        after.put("emergencyPhone", emergency == null ? null : emergency.getPhone());
        auditService.record(empId, "SELF_UPDATE", before, after, "自助信息变更");
        return profile(empId);
    }

    /** 发起关键信息变更申请（EMP_PROFILE_CHG）。 */
    @Transactional(rollbackFor = Exception.class)
    public CriticalChangeResponse requestChange(Long empId, CriticalChangeRequest req) {
        Map<String, Object> changes = new LinkedHashMap<>();
        putIfPresent(changes, "certNo", req.certNo());
        putIfPresent(changes, "certType", req.certType());
        putIfPresent(changes, "bankAccount", req.bankAccount());
        putIfPresent(changes, "hireType", req.hireType());
        if (changes.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "至少提交一个关键字段变更");
        }
        require(empId);
        String bizKey = CodeUtil.next("pc");
        EmpProfileChange row = new EmpProfileChange();
        row.setEmpId(empId);
        row.setBizKey(bizKey);
        row.setChangesJson(JsonUtil.toJson(changes));
        row.setStatus(STATUS_PENDING);
        row.setReason(req.reason());
        changeRequestMapper.insert(row);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empId", empId);
        payload.put("changes", changes);
        payload.put("reason", req.reason());
        String procInstId = flowIntegration.start(TEMPLATE_KEY, bizKey, TraceContext.getTenantId(),
                payload, TraceContext.getUserId());
        row.setProcInstId(procInstId);
        changeRequestMapper.updateById(row);
        return new CriticalChangeResponse(bizKey, TEMPLATE_KEY, procInstId, STATUS_PENDING);
    }

    /** 关键信息变更回调结果；NOT_FOUND 让调用方继续按生命周期单处理。 */
    public enum CallbackResult {
        NOT_FOUND, APPLIED, REJECTED, IDEMPOTENT
    }

    /**
     * 审批回调落账（flow → emp）。
     *
     * @return NOT_FOUND 表示当前 bizKey 不是关键信息变更单（由生命周期单继续处理）
     */
    @Transactional(rollbackFor = Exception.class)
    public CallbackResult handleCallback(String bizKey, boolean approved, String comment) {
        EmpProfileChange row = changeRequestMapper.selectOne(new LambdaQueryWrapper<EmpProfileChange>()
                .eq(EmpProfileChange::getBizKey, bizKey)
                .last("limit 1"));
        if (row == null) {
            return CallbackResult.NOT_FOUND;
        }
        if (!STATUS_PENDING.equals(row.getStatus())) {
            return CallbackResult.IDEMPOTENT;
        }
        if (approved) {
            employeeService.applyProfileChange(row.getEmpId(), JsonUtil.toMap(row.getChangesJson()),
                    row.getReason() == null ? comment : row.getReason());
            row.setStatus(STATUS_APPLIED);
            row.setAppliedAt(LocalDateTime.now());
            changeRequestMapper.updateById(row);
            return CallbackResult.APPLIED;
        }
        row.setStatus(STATUS_REJECTED);
        changeRequestMapper.updateById(row);
        return CallbackResult.REJECTED;
    }

    /** 关键字段拒绝直改：文案直接给出流程模板键，前端可据此跳转发起审批。 */
    private void rejectCritical(SelfProfileUpdateRequest req) {
        if (notBlank(req.certNo()) || notBlank(req.certType())
                || notBlank(req.bankAccount()) || notBlank(req.hireType())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "关键字段（证件号/证件类型/银行卡/雇佣类型）需走信息变更流程，流程模板键 " + TEMPLATE_KEY);
        }
    }

    private ProfileEmergency updateEmergency(Long empId, String name, String phone) {
        if (name == null && phone == null) {
            return firstEmergency(empId);
        }
        ProfileEmergency row = firstEmergency(empId);
        if (row == null) {
            row = new ProfileEmergency();
            row.setEmployeeId(empId);
            row.setRelation("OTHER");
            row.setName(name);
            row.setPhone(phone);
            emergencyMapper.insert(row);
            return row;
        }
        if (name != null) {
            row.setName(name);
        }
        if (phone != null) {
            row.setPhone(phone);
        }
        emergencyMapper.updateById(row);
        return row;
    }

    private ProfileEmergency firstEmergency(Long empId) {
        List<ProfileEmergency> list = emergencyMapper.selectList(new LambdaQueryWrapper<ProfileEmergency>()
                .eq(ProfileEmergency::getEmployeeId, empId)
                .orderByAsc(ProfileEmergency::getId)
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private Employment currentEmployment(Long empId) {
        return employmentService.current(empId);
    }

    private Employee require(Long empId) {
        if (empId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无法识别当前员工身份");
        }
        Employee e = employeeMapper.selectById(empId);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return e;
    }

    private Map<String, Object> snapshot(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", e.getName());
        m.put("mobile", e.getMobileCipher());
        m.put("email", e.getEmail());
        m.put("address", e.getAddress());
        return m;
    }

    private boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (notBlank(value)) {
            map.put(key, value.trim());
        }
    }
}
