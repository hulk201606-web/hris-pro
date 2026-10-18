package com.hris.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

/**
 * 用户账号（一人一账号，离职禁用）。
 *
 * <p>mfa_secret：TOTP 密钥，DDL 注释标「密文」。本增量不涉及列级加密（docs/12 §2.1），
 * 先按明文落库，接入 ShardingSphere 加密插件后由密文列 mfa_secret_cipher 承接。</p>
 */
@TableName("auth_user")
public class AuthUser extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工 ID（逻辑外键 emp_employee.id） */
    private Long employeeId;

    /** 登录用户名（租户内唯一） */
    private String username;

    /** 密码哈希（Argon2id） */
    private String pwdHash;

    /** TOTP 密钥（启用 MFA 后写入） */
    private String mfaSecret;

    /** 状态 1=正常 0=禁用（离职联动） */
    private Integer status;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPwdHash() {
        return pwdHash;
    }

    public void setPwdHash(String pwdHash) {
        this.pwdHash = pwdHash;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public void setMfaSecret(String mfaSecret) {
        this.mfaSecret = mfaSecret;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
