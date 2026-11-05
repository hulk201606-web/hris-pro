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
 * 简历解析（RCS-F-007）：规则解析（正则 + 启发式）简历文本，落库 rcu_resume_parse，
 * 幂等（uk tenant_id+resume_id 一简历一份最新解析，重复 parse 覆盖旧结果），
 * 并回填 candidate 空缺字段（不覆盖人工数据）。
 *
 * <p>诚实约束：本项目无 OCR/NLP 引擎且不依赖外部 API，MVP 规则解析一律
 * parse_status=RULE_LIMITED（姓名为启发式抽取，需人工核对）。</p>
 */
@Service
public class ResumeParseService {

    static final String STATUS_RULE_LIMITED = "RULE_LIMITED";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ResumeMapper resumeMapper;
    private final ResumeParseMapper parseMapper;
    private final CandidateMapper candidateMapper;

    public ResumeParseService(ResumeMapper resumeMapper, ResumeParseMapper parseMapper,
                              CandidateMapper candidateMapper) {
        this.resumeMapper = resumeMapper;
        this.parseMapper = parseMapper;
        this.candidateMapper = candidateMapper;
    }

    /**
     * 触发解析（POST /rcu/resumes/{id}/parse）：取简历文本做规则抽取，uk 幂等覆盖旧结果。
     * 简历以文件引用存储（rcu_resume 仅 file_id），解析输入为请求体携带的纯文本。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResumeParseResponse parse(Long resumeId, ResumeParseRequest req) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Resume 不存在");
        }
        String text = req == null ? null : req.text();
        if (text == null || text.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "简历文本缺失：简历以附件文件引用存储，解析输入需在请求体 text 提供纯文本");
        }

        ResumeTextExtractor.ExtractResult r = ResumeTextExtractor.extract(text);
        String educationJson = toJson(r.educationLines());
        String experienceJson = toJson(r.experienceLines());

        ResumeParse exist = findByResumeId(resumeId);
        if (exist != null) {
            fillParsed(exist, resume.getCandidateId(), r, educationJson, experienceJson);
            parseMapper.updateById(exist);
            backfillCandidate(resume.getCandidateId(), r);
            return toResponse(exist);
        }

        ResumeParse e = new ResumeParse();
        e.setResumeId(resumeId);
        fillParsed(e, resume.getCandidateId(), r, educationJson, experienceJson);
        try {
            parseMapper.insert(e);
        } catch (DuplicateKeyException dup) {
            // 并发重复 parse：uk(tenant_id, resume_id) 冲突转更新，保证幂等
            ResumeParse winner = findByResumeId(resumeId);
            if (winner == null) {
                throw dup;
            }
            fillParsed(winner, resume.getCandidateId(), r, educationJson, experienceJson);
            parseMapper.updateById(winner);
            backfillCandidate(resume.getCandidateId(), r);
            return toResponse(winner);
        }
        backfillCandidate(resume.getCandidateId(), r);
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

    private void fillParsed(ResumeParse e, Long candidateId,
                            ResumeTextExtractor.ExtractResult r,
                            String educationJson, String experienceJson) {
        e.setCandidateId(candidateId);
        e.setParsedName(r.name());
        e.setParsedMobile(r.mobile());
        e.setParsedEmail(r.email());
        e.setEducationJson(educationJson);
        e.setExperienceJson(experienceJson);
        e.setParseStatus(STATUS_RULE_LIMITED);
        e.setParseError(null);
    }

    /**
     * 回填 candidate：仅当对应字段为空时写入解析值，不覆盖人工数据。
     * mobile 同步生成掩码影子列（语义与 CandidateService.create 一致）。
     */
    private void backfillCandidate(Long candidateId, ResumeTextExtractor.ExtractResult r) {
        if (candidateId == null) {
            return;
        }
        Candidate c = candidateMapper.selectById(candidateId);
        if (c == null) {
            return;
        }
        boolean changed = false;
        if (isBlank(c.getName()) && r.name() != null) {
            c.setName(r.name());
            changed = true;
        }
        if (isBlank(c.getMobileCipher()) && r.mobile() != null) {
            c.setMobileCipher(r.mobile());
            c.setMobileMask(MaskUtil.maskMobile(r.mobile()));
            changed = true;
        }
        if (isBlank(c.getEmail()) && r.email() != null) {
            c.setEmail(r.email());
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

    private ResumeParseResponse toResponse(ResumeParse e) {
        return new ResumeParseResponse(e.getId(), e.getResumeId(), e.getCandidateId(),
                e.getParsedName(), e.getParsedMobile(), e.getParsedEmail(),
                e.getEducationJson(), e.getExperienceJson(),
                e.getParseStatus(), e.getParseError(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
