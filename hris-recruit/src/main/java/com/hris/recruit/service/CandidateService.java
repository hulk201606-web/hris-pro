package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.CandidateDtos.CandidateCreateRequest;
import com.hris.recruit.dto.CandidateDtos.CandidateResponse;
import com.hris.recruit.dto.CandidateDtos.CandidateUpdateRequest;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.mapper.CandidateMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.util.MaskUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 候选人主档（Candidate）：人才库核心实体，记录姓名/手机/邮箱（密文+掩码）与候选人状态（待筛选/面试中/Offer/已入职/已淘汰）。
 *
 * <p>业务域：招聘流程（recruit）；分层：Service。</p>
 */
@Service
public class CandidateService {

    private final CandidateMapper mapper;
    private final BlacklistGuard blacklistGuard;

    public CandidateService(CandidateMapper mapper, BlacklistGuard blacklistGuard) {
        this.mapper = mapper;
        this.blacklistGuard = blacklistGuard;
    }

    /** 按 ID 查询，不存在抛 NOT_FOUND。 */
    public CandidateResponse get(Long id) {
        Candidate e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Candidate 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询列表。 */
    public PageResult<CandidateResponse> list(long page, long size) {
        IPage<Candidate> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<CandidateResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /**
     * 新建记录（事务）。入库前先做员工黑名单校验（RCS-F-011）：命中抛 PARAM_INVALID
     * 「候选人命中员工黑名单，禁止入库」；emp 不可用时 fail-open 放行（见 BlacklistGuard）。
     * mobileCipher 语义与现有一致（明文落列，掩码影子列兜底）。
     */
    @Transactional(rollbackFor = Exception.class)
    public CandidateResponse create(CandidateCreateRequest req) {
        blacklistGuard.checkOrThrow(null, req.mobileCipher());
        Candidate e = new Candidate();
        e.setName(req.name());
        e.setMobileCipher(req.mobileCipher());
        e.setMobileMask(effectiveMask(req.mobileMask(), req.mobileCipher()));
        e.setEmail(req.email());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新记录（事务）；数据被并发变更时抛 RCS_ERROR。 */
    @Transactional(rollbackFor = Exception.class)
    public CandidateResponse update(Long id, CandidateUpdateRequest req) {
        Candidate e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Candidate 不存在");
        }
        e.setName(req.name());
        e.setMobileCipher(req.mobileCipher());
        e.setMobileMask(effectiveMask(req.mobileMask(), req.mobileCipher()));
        e.setEmail(req.email());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除记录（事务）；不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Candidate 不存在");
        }
    }

    /** 掩码影子列兜底：调用方未传时按原文在服务侧生成，掩码不依赖外部输入。 */
    private String effectiveMask(String mask, String plain) {
        return mask == null || mask.isBlank() ? MaskUtil.maskMobile(plain) : mask;
    }

    private CandidateResponse toResponse(Candidate e) {
        return new CandidateResponse(e.getId(), e.getName(), MaskUtil.maskMobile(e.getMobileCipher()), e.getEmail(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
