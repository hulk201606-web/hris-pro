package com.hris.employee.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.crypto.CipherTypeHandler;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 员工主档（emp_employee）。证件号/手机号为敏感字段（docs/12 §2.1 加密列 + 掩码影子列）。
 *
 * <p>ADR-007 敏感列加密：certNoCipher / mobileCipher 经 CipherTypeHandler 在 JDBC 边界
 * AES-256-GCM 加密落库（enc1: 前缀），读侧双读兼容存量明文；实体字段保持明文语义，
 * 业务代码零改动。certNoHash（SHA-256）承担等值查重，certNoMask / mobileMask 始终存
 * 脱敏值用于列表展示与检索。</p>
 */
@TableName(value = "emp_employee", autoResultMap = true)
public class Employee extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 员工工号（租户内唯一） */
    private String empNo;

    /** 姓名 */
    private String name;

    /** 姓名拼音排序键（应用生成，规避排序规则差异） */
    private String pinyinSortKey;

    /** 证件类型（默认 ID_CARD） */
    private String certType;

    /** 证件号密文列（JDBC 边界加密，实体侧明文语义，逻辑列 cert_no） */
    @TableField(typeHandler = CipherTypeHandler.EmpCertNo.class)
    private String certNoCipher;

    /** 证件号哈希（SHA-256 小写 hex，等值查重用，不随加密改造失效） */
    private String certNoHash;

    /** 证件号掩码（保留首尾） */
    private String certNoMask;

    /** 手机号密文列（JDBC 边界加密，实体侧明文语义，逻辑列 mobile） */
    @TableField(typeHandler = CipherTypeHandler.EmpMobile.class)
    private String mobileCipher;

    /** 手机号掩码 */
    private String mobileMask;

    /** 邮箱 */
    private String email;

    /** 性别 M/F */
    private String gender;

    /** 出生日期 */
    private LocalDate birthDate;

    /** 在职状态（EmployeeStatus.code） */
    private Integer status;

    /** 是否黑名单 1=是 0=否 */
    private Integer blacklisted;

    public String getEmpNo() {
        return empNo;
    }

    public void setEmpNo(String empNo) {
        this.empNo = empNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPinyinSortKey() {
        return pinyinSortKey;
    }

    public void setPinyinSortKey(String pinyinSortKey) {
        this.pinyinSortKey = pinyinSortKey;
    }

    public String getCertType() {
        return certType;
    }

    public void setCertType(String certType) {
        this.certType = certType;
    }

    public String getCertNoCipher() {
        return certNoCipher;
    }

    public void setCertNoCipher(String certNoCipher) {
        this.certNoCipher = certNoCipher;
    }

    public String getCertNoHash() {
        return certNoHash;
    }

    public void setCertNoHash(String certNoHash) {
        this.certNoHash = certNoHash;
    }

    public String getCertNoMask() {
        return certNoMask;
    }

    public void setCertNoMask(String certNoMask) {
        this.certNoMask = certNoMask;
    }

    public String getMobileCipher() {
        return mobileCipher;
    }

    public void setMobileCipher(String mobileCipher) {
        this.mobileCipher = mobileCipher;
    }

    public String getMobileMask() {
        return mobileMask;
    }

    public void setMobileMask(String mobileMask) {
        this.mobileMask = mobileMask;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getBlacklisted() {
        return blacklisted;
    }

    public void setBlacklisted(Integer blacklisted) {
        this.blacklisted = blacklisted;
    }
}
