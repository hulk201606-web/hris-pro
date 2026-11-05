package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.client.NotifyClient;
import com.hris.recruit.dto.OfferDtos.OfferCreateRequest;
import com.hris.recruit.dto.OfferDtos.OfferReceiptRequest;
import com.hris.recruit.dto.OfferDtos.OfferResponse;
import com.hris.recruit.dto.OfferDtos.OfferSendRequest;
import com.hris.recruit.dto.OfferDtos.OfferSendResponse;
import com.hris.recruit.dto.OfferDtos.OfferUpdateRequest;
import com.hris.recruit.entity.Offer;
import com.hris.recruit.entity.OfferSendLog;
import com.hris.recruit.mapper.OfferMapper;
import com.hris.recruit.mapper.OfferSendLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 录用意向（Offer）：CRUD + RCS-F-018 发送/回执。
 *
 * <p>状态机：DRAFT --send--> SENT --receipt--> ACCEPTED / REJECTED。
 * 仅 DRAFT 可发送；发送失败保持 DRAFT 并落 FAILED 日志（fail-closed，不伪造 SENT）。
 * 回执仅对已发送（SENT/ACCEPTED/REJECTED）登记，重复回执覆盖并刷新 receipt_at 留痕；
 * receiptType=DECLINED 映射既有状态枚举 REJECTED。</p>
 *
 * <p>业务域：招聘流程（recruit）；分层：Service。</p>
 */
@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    /** Offer 状态枚举（与 rcu_offer.status 对齐）。 */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";

    /** 发送日志状态。 */
    public static final String SEND_SENT = "SENT";
    public static final String SEND_FAILED = "FAILED";

    /** 回执类型。 */
    public static final String RECEIPT_ACCEPTED = "ACCEPTED";
    public static final String RECEIPT_DECLINED = "DECLINED";

    private static final Set<String> CHANNELS = Set.of("EMAIL", "WECOM", "DINGTALK");

    private final OfferMapper mapper;
    private final OfferSendLogMapper sendLogMapper;
    private final NotifyClient notifyClient;

    public OfferService(OfferMapper mapper, OfferSendLogMapper sendLogMapper, NotifyClient notifyClient) {
        this.mapper = mapper;
        this.sendLogMapper = sendLogMapper;
        this.notifyClient = notifyClient;
    }

    /** 按 ID 查询，不存在抛 NOT_FOUND。 */
    public OfferResponse get(Long id) {
        return toResponse(requireExists(id));
    }

    /** 分页查询列表。 */
    public PageResult<OfferResponse> list(long page, long size) {
        IPage<Offer> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<OfferResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建记录（事务）。 */
    @Transactional(rollbackFor = Exception.class)
    public OfferResponse create(OfferCreateRequest req) {
        Offer e = new Offer();
        e.setApplicationId(req.applicationId());
        e.setOfferNo(req.offerNo());
        e.setSalaryBandCheck(req.salaryBandCheck());
        e.setSalaryAmountCipher(req.salaryAmountCipher());
        e.setSalaryAmountMask(req.salaryAmountMask());
        e.setStatus(req.status());
        e.setProcInstId(req.procInstId());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新记录（事务）；数据被并发变更时抛 RCS_ERROR。 */
    @Transactional(rollbackFor = Exception.class)
    public OfferResponse update(Long id, OfferUpdateRequest req) {
        Offer e = requireExists(id);
        e.setApplicationId(req.applicationId());
        e.setOfferNo(req.offerNo());
        e.setSalaryBandCheck(req.salaryBandCheck());
        e.setSalaryAmountCipher(req.salaryAmountCipher());
        e.setSalaryAmountMask(req.salaryAmountMask());
        e.setStatus(req.status());
        e.setProcInstId(req.procInstId());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除记录（事务）；不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Offer 不存在");
        }
    }

    /**
     * 发送 Offer（RCS-F-018，事务）：经 PLT 通知能力投递，无论成功失败均落发送日志。
     * PLT 不可用/返回非 SENT 时 status=FAILED + error 透出，Offer 保持 DRAFT。
     */
    @Transactional(rollbackFor = Exception.class)
    public OfferSendResponse send(Long id, OfferSendRequest req) {
        Offer e = requireExists(id);
        if (!STATUS_DRAFT.equals(e.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "仅草稿状态可发送 Offer，当前状态：" + e.getStatus());
        }
        String channel = normalizeChannel(req.channel());
        String error = dispatch(e, channel, req.recipient());

        OfferSendLog logRow = new OfferSendLog();
        logRow.setOfferId(e.getId());
        logRow.setChannel(channel);
        logRow.setStatus(error == null ? SEND_SENT : SEND_FAILED);
        if (error != null) {
            logRow.setError(truncate(error));
        } else {
            logRow.setSentAt(LocalDateTime.now());
        }
        sendLogMapper.insert(logRow);

        if (error != null) {
            return new OfferSendResponse(e.getId(), logRow.getId(), SEND_FAILED, error, e.getStatus());
        }
        e.setStatus(STATUS_SENT);
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return new OfferSendResponse(e.getId(), logRow.getId(), SEND_SENT, null, e.getStatus());
    }

    /**
     * 候选人回执登记（RCS-F-018，事务）：仅已发送 Offer 可登记；重复回执覆盖 receipt 字段
     * 并以 receipt_at/updated_at 留痕（幂等：同类型重复回执结果一致）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OfferResponse receipt(Long id, OfferReceiptRequest req) {
        Offer e = requireExists(id);
        if (STATUS_DRAFT.equals(e.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Offer 尚未发送，不能登记回执");
        }
        String type = normalizeReceiptType(req.type());
        e.setReceiptType(type);
        e.setReceiptNote(req.note());
        e.setReceiptAt(LocalDateTime.now());
        e.setStatus(RECEIPT_ACCEPTED.equals(type) ? STATUS_ACCEPTED : STATUS_REJECTED);
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /**
     * 调用 PLT 通知能力。返回 null=发送成功；非 null=失败原因（fail-closed，不抛异常以保留 FAILED 日志）。
     */
    private String dispatch(Offer e, String channel, String recipient) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel);
        body.put("recipient", recipient);
        body.put("subject", "录用意向通知书 " + e.getOfferNo());
        body.put("content", "您好，附件为录用意向通知书（编号：" + e.getOfferNo() + "），请查收并回复。");
        body.put("bizType", "RCS_OFFER");
        body.put("bizKey", String.valueOf(e.getId()));
        try {
            R<Map<String, Object>> resp = notifyClient.notify(body);
            if (resp == null || !resp.isSuccess()) {
                return "通知服务返回失败：" + (resp == null ? "无响应" : resp.getMessage());
            }
            Map<String, Object> data = resp.getData();
            Object status = data == null ? null : data.get("status");
            if (status == null || !SEND_SENT.equalsIgnoreCase(String.valueOf(status))) {
                Object err = data == null ? null : data.get("error");
                return err == null ? "通知服务未确认发送成功" : String.valueOf(err);
            }
            return null;
        } catch (Exception ex) {
            log.warn("Offer 发送失败（PLT 通知不可用，fail-closed）: offerId={}, channel={}, err={}",
                    e.getId(), channel, ex.getMessage());
            return "通知服务不可用：" + ex.getMessage();
        }
    }

    private String normalizeChannel(String channel) {
        String ch = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(ch)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "channel 仅支持 EMAIL/WECOM/DINGTALK");
        }
        return ch;
    }

    private String normalizeReceiptType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!RECEIPT_ACCEPTED.equals(t) && !RECEIPT_DECLINED.equals(t)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "type 仅支持 ACCEPTED/DECLINED");
        }
        return t;
    }

    private String truncate(String error) {
        return error.length() <= 500 ? error : error.substring(0, 500);
    }

    private Offer requireExists(Long id) {
        Offer e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Offer 不存在");
        }
        return e;
    }

    private OfferResponse toResponse(Offer e) {
        return new OfferResponse(e.getId(), e.getApplicationId(), e.getOfferNo(), e.getSalaryBandCheck(),
                e.getSalaryAmountCipher(), e.getSalaryAmountMask(), e.getStatus(), e.getProcInstId(),
                e.getReceiptType(), e.getReceiptNote(), e.getReceiptAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
