package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.util.MaskUtil;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseRequest;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseResponse;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseUpdateRequest;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.entity.Resume;
import com.hris.recruit.entity.ResumeParse;
import com.hris.recruit.mapper.CandidateMapper;
import com.hris.recruit.mapper.ResumeMapper;
import com.hris.recruit.mapper.ResumeParseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 简历解析（RCS-F-007）：规则解析（正则 + 启发式）与 AI 解析（DashScope LLM）双模式，
 * 落库 rcu_resume_parse，幂等（uk tenant_id+resume_id 一简历一份最新解析，重复 parse 覆盖旧结果），
 * 并回填 candidate 空缺字段（不覆盖人工数据）。
 *
 * <p>模式语义（POST /rcu/resumes/{id}/parse?mode=rule|ai，缺省 rule 兼容）：
 * rule → parse_status=RULE_LIMITED（姓名为启发式抽取，需人工核对）；
 * ai → 结构化抽取（姓名/手机/邮箱/教育数组/工作经历数组），parse_status=AI_PARSED；
 * LLM 调用失败/超时直接报错（AI 解析失败：原因），旧解析结果保持不变，不静默降级。</p>
 */
@Service
public class ResumeParseService {

    static final String STATUS_RULE_LIMITED = "RULE_LIMITED";
    static final String STATUS_AI_PARSED = "AI_PARSED";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ResumeMapper resumeMapper;
    private final ResumeParseMapper parseMapper;
    private final CandidateMapper candidateMapper;
    private final DashScopeResumeAiClient aiClient;

    public ResumeParseService(ResumeMapper resumeMapper, ResumeParseMapper parseMapper,
                              CandidateMapper candidateMapper, DashScopeResumeAiClient aiClient) {
        this.resumeMapper = resumeMapper;
        this.parseMapper = parseMapper;
        this.candidateMapper = candidateMapper;
        this.aiClient = aiClient;
    }

    /** 解析入口：mode=ai 走 LLM 抽取，其余（含缺省）走规则解析。 */
    @Transactional(rollbackFor = Exception.class)
    public ResumeParseResponse parse(Long resumeId, String mode, ResumeParseRequest req) {
        if ("ai".equalsIgnoreCase(mode)) {
            return parseAi(resumeId, req);
        }
        return parseRule(resumeId, req);
    }

    /** 触发规则解析（POST /rcu/resumes/{id}/parse，mode 缺省）：兼容旧签名。 */
    @Transactional(rollbackFor = Exception.class)
    public ResumeParseResponse parse(Long resumeId, ResumeParseRequest req) {
        return parseRule(resumeId, req);
    }

    /**
     * 规则解析：取简历文本做规则抽取，uk 幂等覆盖旧结果。
     * 简历以文件引用存储（rcu_resume 仅 file_id），解析输入为请求体携带的纯文本。
     */
    private ResumeParseResponse parseRule(Long resumeId, ResumeParseRequest req) {
        Resume resume = requireResumeAndText(resumeId, req);
        ResumeTextExtractor.ExtractResult r = ResumeTextExtractor.extract(req.text());
        return upsert(resume, new Payload(r.name(), r.mobile(), r.email(),
                toJson(r.educationLines()), toJson(r.experienceLines()), STATUS_RULE_LIMITED));
    }

    /**
     * AI 解析：DashScope LLM 结构化抽取，parse_status=AI_PARSED。
     * LLM 失败抛 BizException（错误信息注明 AI 解析失败原因），旧解析结果保持不变。
     */
    private ResumeParseResponse parseAi(Long resumeId, ResumeParseRequest req) {
        Resume resume = requireResumeAndText(resumeId, req);
        DashScopeResumeAiClient.AiExtraction ai = aiClient.extract(req.text());
        return upsert(resume, new Payload(ai.name(), ai.mobile(), ai.email(),
                ai.educationJson(), ai.experienceJson(), STATUS_AI_PARSED));
    }

    /** 校验简历存在且文本非空（规则/AI 共用前置）。 */
    private Resume requireResumeAndText(Long resumeId, ResumeParseRequest req) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Resume 不存在");
        }
        String text = req == null ? null : req.text();
        if (text == null || text.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "简历文本缺失：简历以附件文件引用存储，解析输入需在请求体 text 提供纯文本");
        }
        return resume;
    }

    /** uk 幂等落库：存在则覆盖，否则插入（并发冲突转更新）；随后回填 candidate 空缺字段。 */
    private ResumeParseResponse upsert(Resume resume, Payload payload) {
        Long resumeId = resume.getId();
        ResumeParse exist = findByResumeId(resumeId);
        if (exist != null) {
            fillParsed(exist, resume.getCandidateId(), payload);
            parseMapper.updateById(exist);
            backfillCandidate(resume.getCandidateId(), payload);
            return toResponse(exist);
        }

        ResumeParse e = new ResumeParse();
        e.setResumeId(resumeId);
        fillParsed(e, resume.getCandidateId(), payload);
        try {
            parseMapper.insert(e);
        } catch (DuplicateKeyException dup) {
            // 并发重复 parse：uk(tenant_id, resume_id) 冲突转更新，保证幂等
            ResumeParse winner = findByResumeId(resumeId);
            if (winner == null) {
                throw dup;
            }
            fillParsed(winner, resume.getCandidateId(), payload);
            parseMapper.updateById(winner);
            backfillCandidate(resume.getCandidateId(), payload);
            return toResponse(winner);
        }
        backfillCandidate(resume.getCandidateId(), payload);
        return toResponse(e);
    }

    /** 查询最新解析结果（GET）：一简历一份，无解析记录抛 NOT_FOUND。 */
    public ResumeParseResponse get(Long resumeId) {
        ResumeParse e = findByResumeId(resumeId);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该简历尚无解析结果");
        }
        return toResponse(e);
    }

    /** 人工修正（PUT）：可改任意 parsed 字段，parse_status 保持不变，updated_by 由 MetaObjectHandler 留痕。 */
    @Transactional(rollbackFor = Exception.class)
    public ResumeParseResponse update(Long resumeId, ResumeParseUpdateRequest req) {
        ResumeParse e = findByResumeId(resumeId);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该简历尚无解析结果");
        }
        if (req.parsedName() != null) {
            e.setParsedName(req.parsedName());
        }
        if (req.parsedMobile() != null) {
            e.setParsedMobile(req.parsedMobile());
        }
        if (req.parsedEmail() != null) {
            e.setParsedEmail(req.parsedEmail());
        }
        if (req.educationJson() != null) {
            e.setEducationJson(req.educationJson());
        }
        if (req.experienceJson() != null) {
            e.setExperienceJson(req.experienceJson());
        }
        if (parseMapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    private ResumeParse findByResumeId(Long resumeId) {
        return parseMapper.selectOne(new LambdaQueryWrapper<ResumeParse>()
                .eq(ResumeParse::getResumeId, resumeId)
                .last("LIMIT 1"));
    }

    private void fillParsed(ResumeParse e, Long candidateId, Payload p) {
        e.setCandidateId(candidateId);
        e.setParsedName(p.name());
        e.setParsedMobile(p.mobile());
        e.setParsedEmail(p.email());
        e.setEducationJson(p.educationJson());
        e.setExperienceJson(p.experienceJson());
        e.setParseStatus(p.status());
        e.setParseError(null);
    }

    /**
     * 回填 candidate：仅当对应字段为空时写入解析值，不覆盖人工数据。
     * mobile 同步生成掩码影子列（语义与 CandidateService.create 一致）。
     */
    private void backfillCandidate(Long candidateId, Payload p) {
        if (candidateId == null) {
            return;
        }
        Candidate c = candidateMapper.selectById(candidateId);
        if (c == null) {
            return;
        }
        boolean changed = false;
        if (isBlank(c.getName()) && p.name() != null) {
            c.setName(p.name());
            changed = true;
        }
        if (isBlank(c.getMobileCipher()) && p.mobile() != null) {
            c.setMobileCipher(p.mobile());
            c.setMobileMask(MaskUtil.maskMobile(p.mobile()));
            changed = true;
        }
        if (isBlank(c.getEmail()) && p.email() != null) {
            c.setEmail(p.email());
            changed = true;
        }
        if (changed) {
            candidateMapper.updateById(c);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String toJson(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(lines);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.RCS_ERROR, "解析结果序列化失败");
        }
    }

    /** 解析载荷（规则/AI 统一载体）。 */
    private record Payload(String name, String mobile, String email,
                           String educationJson, String experienceJson, String status) {
    }

    private ResumeParseResponse toResponse(ResumeParse e) {
        return new ResumeParseResponse(e.getId(), e.getResumeId(), e.getCandidateId(),
                e.getParsedName(), e.getParsedMobile(), e.getParsedEmail(),
                e.getEducationJson(), e.getExperienceJson(),
                e.getParseStatus(), e.getParseError(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
