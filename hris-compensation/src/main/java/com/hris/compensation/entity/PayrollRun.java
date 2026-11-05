package com.hris.compensation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;


/**
 * 算薪批次（域：薪酬，分层：实体/DO）。状态机 DRAFT→CALCULATING→LOCKED→PAID→ARCHIVED，锁单后不可修改。
 *
 * <p>双人复核（CMP-F-013b）：reviewed_by_1/reviewed_by_2 为两名不同复核人，两列均落库且
 * 互不相同才满足 REVIEW→APPROVE 条件；任一为 NULL 表示复核未完成。review_comment 保存
 * 最近一次复核意见/驳回理由。</p>
 */
@TableName("cmp_payroll_run")
public class PayrollRun extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private String periodCode;  // 周期编码(如 2026-01)

    public String getPeriodCode() {
        return periodCode;
    }

    public void setPeriodCode(String periodCode) {
        this.periodCode = periodCode;
    }
    private Long payGroupId;  // 薪资组ID(逻辑外键 cmp_pay_group.id)

    public Long getPayGroupId() {
        return payGroupId;
    }

    public void setPayGroupId(Long payGroupId) {
        this.payGroupId = payGroupId;
    }
    private String status;  // 状态 INIT/TRIAL/CALC/REVIEW/APPROVE/PAID/LOCK

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    private Long calcBy;  // 计算人ID

    public Long getCalcBy() {
        return calcBy;
    }

    public void setCalcBy(Long calcBy) {
        this.calcBy = calcBy;
    }
    private Long reviewBy;  // 复核人ID

    public Long getReviewBy() {
        return reviewBy;
    }

    public void setReviewBy(Long reviewBy) {
        this.reviewBy = reviewBy;
    }
    @TableField("reviewed_by_1")
    private Long reviewedBy1;  // 第一位复核人ID(NULL=未复核)

    public Long getReviewedBy1() {
        return reviewedBy1;
    }

    public void setReviewedBy1(Long reviewedBy1) {
        this.reviewedBy1 = reviewedBy1;
    }
    @TableField("reviewed_at_1")
    private LocalDateTime reviewedAt1;  // 第一位复核时间

    public LocalDateTime getReviewedAt1() {
        return reviewedAt1;
    }

    public void setReviewedAt1(LocalDateTime reviewedAt1) {
        this.reviewedAt1 = reviewedAt1;
    }
    @TableField("reviewed_by_2")
    private Long reviewedBy2;  // 第二位复核人ID(NULL=未复核)，须与第一位不同

    public Long getReviewedBy2() {
        return reviewedBy2;
    }

    public void setReviewedBy2(Long reviewedBy2) {
        this.reviewedBy2 = reviewedBy2;
    }
    @TableField("reviewed_at_2")
    private LocalDateTime reviewedAt2;  // 第二位复核时间

    public LocalDateTime getReviewedAt2() {
        return reviewedAt2;
    }

    public void setReviewedAt2(LocalDateTime reviewedAt2) {
        this.reviewedAt2 = reviewedAt2;
    }
    private String reviewComment;  // 最近一次复核意见/驳回理由

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }
}
