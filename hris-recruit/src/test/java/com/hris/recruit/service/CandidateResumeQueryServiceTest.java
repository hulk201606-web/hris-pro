package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.ResumeDtos.ResumeResponse;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseResponse;
import com.hris.recruit.entity.Resume;
import com.hris.recruit.entity.ResumeParse;
import com.hris.recruit.mapper.CandidateMapper;
import com.hris.recruit.mapper.ResumeMapper;
import com.hris.recruit.mapper.ResumeParseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CandidateResumeQueryService 单元测试（批 6 #8）：
 * 关联查询语义 / 候选人不存在 NOT_FOUND / 无简历空列表 / 无解析 NOT_FOUND。
 */
@ExtendWith(MockitoExtension.class)
class CandidateResumeQueryServiceTest {

    private static final Long CANDIDATE_ID = 200L;
    private static final Long RESUME_ID = 100L;

    @Mock
    private CandidateMapper candidateMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeParseMapper parseMapper;

    private CandidateResumeQueryService service;

    @BeforeAll
    static void initTableInfo() {
        // 纯 Mockito 环境无 MP 运行时，手动注册实体 lambda 缓存（LambdaQueryWrapper 解析列名依赖）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Resume.class);
        TableInfoHelper.initTableInfo(assistant, ResumeParse.class);
    }

    @BeforeEach
    void setUp() {
        service = new CandidateResumeQueryService(candidateMapper, resumeMapper, parseMapper);
        lenient().when(candidateMapper.selectById(CANDIDATE_ID)).thenReturn(new com.hris.recruit.entity.Candidate());
    }

    private Resume resume(Long id) {
        Resume r = new Resume();
        r.setId(id);
        r.setCandidateId(CANDIDATE_ID);
        return r;
    }

    @Test
    @DisplayName("resumes：返回候选人简历列表（映射 id/candidateId/fileId/parseResult）")
    void resumesReturnsList() {
        Resume r = resume(RESUME_ID);
        r.setFileId(555L);
        r.setParseResult("{\"selfIntro\":\"hi\"}");
        when(resumeMapper.selectList(any())).thenReturn(List.of(r));

        List<ResumeResponse> list = service.resumes(CANDIDATE_ID);

        assertEquals(1, list.size());
        assertEquals(RESUME_ID, list.get(0).id());
        assertEquals(CANDIDATE_ID, list.get(0).candidateId());
        assertEquals(555L, list.get(0).fileId());
        assertEquals("{\"selfIntro\":\"hi\"}", list.get(0).parseResult());
    }

    @Test
    @DisplayName("resumes：候选人存在但无简历 → 空列表（非异常）")
    void resumesEmptyWhenNoResume() {
        when(resumeMapper.selectList(any())).thenReturn(List.of());
        assertEquals(List.of(), service.resumes(CANDIDATE_ID));
    }

    @Test
    @DisplayName("resumes：候选人不存在 → NOT_FOUND")
    void resumesCandidateNotFound() {
        when(candidateMapper.selectById(CANDIDATE_ID)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.resumes(CANDIDATE_ID));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("resume-parse：多条解析取 updated_at 最新一条")
    void latestParseReturnsNewest() {
        when(resumeMapper.selectList(any())).thenReturn(List.of(resume(RESUME_ID), resume(101L)));
        ResumeParse newest = new ResumeParse();
        newest.setId(9L);
        newest.setResumeId(RESUME_ID);
        newest.setCandidateId(CANDIDATE_ID);
        newest.setParsedName("张三");
        newest.setParseStatus("AI_PARSED");
        when(parseMapper.selectList(any())).thenReturn(List.of(newest));

        ResumeParseResponse resp = service.latestParse(CANDIDATE_ID);

        assertEquals(9L, resp.id());
        assertEquals("张三", resp.parsedName());
        assertEquals("AI_PARSED", resp.parseStatus());
    }

    @Test
    @DisplayName("resume-parse：候选人无任何简历 → NOT_FOUND")
    void latestParseNotFoundWhenNoResume() {
        when(resumeMapper.selectList(any())).thenReturn(List.of());
        BizException e = assertThrows(BizException.class, () -> service.latestParse(CANDIDATE_ID));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("resume-parse：有简历但均无解析记录 → NOT_FOUND")
    void latestParseNotFoundWhenNoParse() {
        when(resumeMapper.selectList(any())).thenReturn(List.of(resume(RESUME_ID)));
        when(parseMapper.selectList(any())).thenReturn(List.of());
        BizException e = assertThrows(BizException.class, () -> service.latestParse(CANDIDATE_ID));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("resume-parse：候选人不存在 → NOT_FOUND")
    void latestParseCandidateNotFound() {
        when(candidateMapper.selectById(CANDIDATE_ID)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.latestParse(CANDIDATE_ID));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }
}
