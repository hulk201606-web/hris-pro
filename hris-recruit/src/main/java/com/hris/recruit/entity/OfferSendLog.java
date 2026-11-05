package com.hris.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * rcu_offer_send_log（RCS-F-018 Offer 发送日志）：每次发送留痕，status=SENT/FAILED。
 */
@TableName("rcu_offer_send_log")
public class OfferSendLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long offerId;

    private String channel;

    private String status;

    private String error;

    private LocalDateTime sentAt;

    public Long getOfferId() {
        return offerId;
    }

    public void setOfferId(Long offerId) {
        this.offerId = offerId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
