package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.employee.dto.BlacklistCheckDtos.BlacklistCheckResponse;
import com.hris.employee.entity.Employee;
import com.hris.employee.entity.StatusLog;
import com.hris.employee.mapper.EmployeeMapper;
import com.hris.employee.mapper.StatusLogMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 黑名单校验（RCS-F-011 招聘域契约，供 /internal/emp/blacklist-check 消费）。
 *
 * <p>匹配口径：certNoHash 按 SHA-256 小写 hex 等值比对（不随加密改造失效）；
 * mobile 等值比对 mobile_cipher 现值——MVP 该列为明文占位，语义与明文手机号一致，
 * 后续接入加密插件后由密文等值承接，调用方契约不变。命中 blacklisted=1 的
 * 员工即返回 blacklisted=true。</p>
 */
@Service
public class BlacklistCheckService {

    /** 固定文案：命中但无状态流水可取时使用 */
    public static final String DEFAULT_REASON = "该员工已列入黑名单";

    private final EmployeeMapper employeeMapper;
    private final StatusLogMapper statusLogMapper;

    public BlacklistCheckService(EmployeeMapper employeeMapper, StatusLogMapper statusLogMapper) {
        this.employeeMapper = employeeMapper;
        this.statusLogMapper = statusLogMapper;
    }

    /**
     * 校验是否命中黑名单。certNoHash / mobile 至少提供其一。
     * 租户上下文由网关透传的 X-Tenant-Id 经租户插件注入，本方法不感知租户。
     */
    public BlacklistCheckResponse check(String certNoHash, String mobile) {
        boolean hasHash = certNoHash != null && !certNoHash.isBlank();
        boolean hasMobile = mobile != null && !mobile.isBlank();
        if (!hasHash && !hasMobile) {
            throw new BizException(ErrorCode.PARAM_INVALID, "certNoHash 与 mobile 至少提供其一");
        }

        String hash = hasHash ? certNoHash.trim().toLowerCase(Locale.ROOT) : null;
        String mobileVal = hasMobile ? mobile.trim() : null;
        LambdaQueryWrapper<Employee> qw = new LambdaQueryWrapper<Employee>()
                .eq(Employee::getBlacklisted, 1);
        if (hasHash && hasMobile) {
            qw.and(n -> n.eq(Employee::getCertNoHash, hash).or()
                    .eq(Employee::getMobileCipher, mobileVal));
        } else if (hasHash) {
            qw.eq(Employee::getCertNoHash, hash);
        } else {
            qw.eq(Employee::getMobileCipher, mobileVal);
        }

        List<Employee> hits = employeeMapper.selectList(qw);
        if (hits.isEmpty()) {
            return new BlacklistCheckResponse(false, null);
        }
        return new BlacklistCheckResponse(true, latestReason(hits.get(0).getId()));
    }

    /** 取该员工最近一条状态流水原因；无流水回退固定文案（blacklisted 本身即事实依据）。 */
    private String latestReason(Long empId) {
        StatusLog log = statusLogMapper.selectOne(new LambdaQueryWrapper<StatusLog>()
                .eq(StatusLog::getEmpId, empId)
                .orderByDesc(StatusLog::getId)
                .last("LIMIT 1"));
        return log == null || log.getReason() == null || log.getReason().isBlank()
                ? DEFAULT_REASON
                : log.getReason();
    }
}
