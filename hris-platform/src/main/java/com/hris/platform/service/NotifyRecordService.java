package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.platform.dto.NotificationDtos.RecordResponse;
import com.hris.platform.dto.PageResult;
import com.hris.platform.entity.SysNotifyRecord;
import com.hris.platform.mapper.SysNotifyRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知记录查询（PLT-F-003）。渲染+落库发送记录，MVP 渠道投递为 stub（状态置 SENT 并留 MQ 消费入口）。
 */
@Service
public class NotifyRecordService {

    private final SysNotifyRecordMapper mapper;

    public NotifyRecordService(SysNotifyRecordMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<RecordResponse> query(Long userId, String channel, String sendStatus,
                                            long page, long size) {
        LambdaQueryWrapper<SysNotifyRecord> qw = new LambdaQueryWrapper<>();
        if (userId != null) {
            qw.eq(SysNotifyRecord::getUserId, userId);
        }
        if (channel != null && !channel.isBlank()) {
            qw.eq(SysNotifyRecord::getChannel, channel.trim().toUpperCase());
        }
        if (sendStatus != null && !sendStatus.isBlank()) {
            qw.eq(SysNotifyRecord::getSendStatus, sendStatus.trim().toUpperCase());
        }
        qw.orderByDesc(SysNotifyRecord::getCreatedAt);
        Page<SysNotifyRecord> p = mapper.selectPage(new Page<>(page, size), qw);
        List<RecordResponse> records = p.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, p.getCurrent(), p.getSize(), p.getTotal());
    }

    /** 渲染结果落库为通知记录；MVP 直接置 SENT（渠道投递由调度/MQ 消费入口接管）。 */
    public RecordResponse record(Long userId, Long templateId, String channel) {
        SysNotifyRecord r = new SysNotifyRecord();
        r.setUserId(userId);
        r.setTemplateId(templateId);
        r.setChannel(channel);
        r.setSendStatus("SENT");
        r.setSentAt(LocalDateTime.now());
        mapper.insert(r);
        return toResponse(r);
    }

    private RecordResponse toResponse(SysNotifyRecord r) {
        return new RecordResponse(r.getId(), r.getUserId(), r.getTemplateId(), r.getChannel(),
                r.getSendStatus(), r.getSentAt(), r.getCreatedAt());
    }
}
