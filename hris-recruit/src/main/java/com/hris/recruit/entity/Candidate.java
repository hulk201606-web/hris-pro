package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;


/**
 * rcu_candidate（由 DDL 自动生成）
 */
@TableName("rcu_candidate")
public class Candidate extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String name;  // 候选人姓名

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    private String mobileCipher;  // 手机号密文(逻辑列 mobile)

    public String getMobileCipher() {
        return mobileCipher;
    }

    public void setMobileCipher(String mobileCipher) {
        this.mobileCipher = mobileCipher;
    }
    private String mobileMask;  // 手机号掩码

    public String getMobileMask() {
        return mobileMask;
    }

    public void setMobileMask(String mobileMask) {
        this.mobileMask = mobileMask;
    }
    private String email;  // 邮箱

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    private String status;  // 状态 NEW/SCREENING/INTERVIEWING/OFFERED/HIRED/REJECTED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
