package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.ResumeDtos.ResumeResponse;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseResponse;
import com.hris.recruit.entity.Resume;
import com.hris.recruit.mapper.CandidateMapper;
import com.hris.recruit.mapper.ResumeMapper;
import com.hris.recruit.mapper.ResumeParseMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 候选人与简历联动查询（批 6 #8）：候选人维度聚合简历列表与最新解析结果。
 *
 * <p>语义约定：
 * <ul>
 *   <li>候选人不存在 → NOT_FOUND（区别于「存在但无简历」）；</li>
 *   <li>简历列表：存在但无简历 → 空数组（R.ok 包裹，前端按 length 判空）；</li>
 *   <li>最新解析：无任何简历，或简历均无解析记录 → NOT_FOUND（R 语义 code=10002）。</li>
 * </ul>
 * 租户隔离由 MyBatis-Plus 租户插件按 TraceContext 兜底。</p>
 */
@Service
public class CandidateResumeQueryService {

    private final CandidateMapper candidateMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeParseMapper parseMapper;

    public CandidateResumeQueryService(CandidateMapper candidateMapper, ResumeMapper resumeMapper,
                                       ResumeParseMapper parseMapper) {
        this.candidateMapper = candidateMapper;
        this.resumeMapper = resumeMapper;
        this.parseMapper = parseMapper;
    }

    /** 候选人简历列表（创建时间升序）；候选人不存在抛 NOT_FOUND，无简历返回空列表。 */
    public List<ResumeResponse> resumes(Long candidateId) {
        requireCandidate(candidateId);
        List<Resume> resumes = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getCandidateId, candidateId)
                .orderByAsc(Resume::getCreatedAt));
        return resumes.stream().map(this::toResponse).toList();
    }

    /** 候选人最新解析结果（经 resume 关联，按 parse.updated_at 取最新）；无简历/无解析抛 NOT_FOUND。 */
    public ResumeParseResponse latestParse(Long candidateId) {
        requireCandidate(candidateId);
        List<Long> resumeIds = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getCandidateId, candidateId)
                        .select(Resume::getId))
                .stream().map(Resume::getId).toList();
        if (resumeIds.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "该候选人尚无简历，无解析结果");
        }
        List<com.hris.recruit.entity.ResumeParse> parses = parseMapper.selectList(
                new LambdaQueryWrapper<com.hris.recruit.entity.ResumeParse>()
                        .in(com.hris.recruit.entity.ResumeParse::getResumeId, resumeIds)
                        .orderByDesc(com.hris.recruit.entity.ResumeParse::getUpdatedAt)
                        .last("LIMIT 1"));
        if (parses.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "该候选人的简历尚无解析结果");
        }
        com.hris.recruit.entity.ResumeParse p = parses.get(0);
        return new ResumeParseResponse(p.getId(), p.getResumeId(), p.getCandidateId(),
                p.getParsedName(), p.getParsedMobile(), p.getParsedEmail(),
                p.getEducationJson(), p.getExperienceJson(),
                p.getParseStatus(), p.getParseError(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private void requireCandidate(Long candidateId) {
        if (candidateMapper.selectById(candidateId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "候选人不存在");
        }
    }

    private ResumeResponse toResponse(Resume r) {
        return new ResumeResponse(r.getId(), r.getCandidateId(), r.getFileId(),
                r.getParseResult(), r.getRetentionUntil(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
