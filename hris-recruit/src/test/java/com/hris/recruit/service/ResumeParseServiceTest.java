package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseRequest;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseResponse;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseUpdateRequest;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.entity.Resume;
import com.hris.recruit.entity.ResumeParse;
import com.hris.recruit.mapper.CandidateMapper;
import com.hris.recruit.mapper.ResumeMapper;
import com.hris.recruit.mapper.ResumeParseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResumeParseService 单元测试（RCS-F-007）：幂等覆盖、回填不覆盖人工值、状态 RULE_LIMITED。
 */
@ExtendWith(MockitoExtension.class)
class ResumeParseServiceTest {

    private static final Long RESUME_ID = 100L;
    private static final Long CANDIDATE_ID = 200L;
    private static final String RESUME_TEXT = """
            张三
            13812348888 | zhangsan@x.com

            工作经历
            2018-2021 某公司 后端工程师
            """;

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeParseMapper parseMapper;
    @Mock
    private CandidateMapper candidateMapper;

    private ResumeParseService service;
    private Resume resume;
    private Candidate candidate;

    @BeforeEach
    void setUp() {
        service = new ResumeParseService(resumeMapper, parseMapper, candidateMapper);
        resume = new Resume();
        resume.setId(RESUME_ID);
        resume.setCandidateId(CANDIDATE_ID);
        candidate = new Candidate();
        candidate.setId(CANDIDATE_ID);
        candidate.setName("人工录入名");
        lenient().when(resumeMapper.selectById(RESUME_ID)).thenReturn(resume);
        lenient().when(candidateMapper.selectById(CANDIDATE_ID)).thenReturn(candidate);
    }

    @Test
    @DisplayName("parse 首次：插入解析记录，status=RULE_LIMITED，回填空字段")
    void parseInsertsAndBackfills() {
        when(parseMapper.selectOne(any())).thenReturn(null);
        candidate.setMobileCipher(null);
        candidate.setEmail(null);

        ResumeParseResponse resp = service.parse(RESUME_ID, new ResumeParseRequest(RESUME_TEXT));

        assertEquals("RULE_LIMITED", resp.parseStatus(), "规则解析必须如实标注 RULE_LIMITED");
        assertEquals("张三", resp.parsedName());
        assertEquals("13812348888", resp.parsedMobile());
        assertEquals("zhangsan@x.com", resp.parsedEmail());
        verify(parseMapper).insert(any(ResumeParse.class));
        ArgumentCaptor<Candidate> cap = ArgumentCaptor.forClass(Candidate.class);
        verify(candidateMapper).updateById(cap.capture());
        assertEquals("13812348888", cap.getValue().getMobileCipher());
        assertEquals("138****8888", cap.getValue().getMobileMask());
    }

    @Test
    @DisplayName("parse 幂等：已存在解析记录时转更新，不新增行")
    void parseIdempotentOverwrite() {
        ResumeParse exist = new ResumeParse();
        exist.setId(999L);
        exist.setResumeId(RESUME_ID);
        exist.setCandidateId(CANDIDATE_ID);
        exist.setParsedName("旧名");
        exist.setParseStatus("RULE_LIMITED");
        when(parseMapper.selectOne(any())).thenReturn(exist);

        ResumeParseResponse resp = service.parse(RESUME_ID, new ResumeParseRequest(RESUME_TEXT));

        verify(parseMapper, never()).insert(any(ResumeParse.class));
        verify(parseMapper).updateById(exist);
        assertEquals("张三", resp.parsedName(), "重复 parse 覆盖旧结果");
        assertEquals(999L, resp.id(), "仍是同一份解析记录");
    }

    @Test
    @DisplayName("回填不覆盖人工数据：candidate 已有 name/mobile/email 时不更新")
    void backfillDoesNotOverwriteManualData() {
        when(parseMapper.selectOne(any())).thenReturn(null);
        candidate.setName("人工录入名");
        candidate.setMobileCipher("13900000000");
        candidate.setEmail("manual@x.com");

        service.parse(RESUME_ID, new ResumeParseRequest(RESUME_TEXT));

        verify(candidateMapper, never()).updateById(any(Candidate.class));
        assertEquals("人工录入名", candidate.getName());
        assertEquals("13900000000", candidate.getMobileCipher());
        assertEquals("manual@x.com", candidate.getEmail());
    }

    @Test
    @DisplayName("parse：简历文本缺失抛 PARAM_INVALID")
    void parseRequiresText() {
        assertThrows(BizException.class, () -> service.parse(RESUME_ID, new ResumeParseRequest(" ")));
        BizException e = assertThrows(BizException.class, () -> service.parse(RESUME_ID, null));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("parse：简历不存在抛 NOT_FOUND")
    void parseResumeNotFound() {
        when(resumeMapper.selectById(RESUME_ID)).thenReturn(null);
        BizException e = assertThrows(BizException.class,
                () -> service.parse(RESUME_ID, new ResumeParseRequest(RESUME_TEXT)));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("get：无解析记录抛 NOT_FOUND")
    void getNotFound() {
        when(parseMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.get(RESUME_ID));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("update 人工修正：parse_status 保持不变，updated_by 留痕由填充器处理，仅更新传入字段")
    void updateKeepsParseStatus() {
        ResumeParse exist = new ResumeParse();
        exist.setId(999L);
        exist.setResumeId(RESUME_ID);
        exist.setParsedName("张三");
        exist.setParsedMobile("13812348888");
        exist.setParseStatus("RULE_LIMITED");
        when(parseMapper.selectOne(any())).thenReturn(exist);
        when(parseMapper.updateById(any(ResumeParse.class))).thenReturn(1);

        ResumeParseResponse resp = service.update(RESUME_ID,
                new ResumeParseUpdateRequest("张三丰", null, "fixed@x.com", null, null));

        assertEquals("张三丰", resp.parsedName(), "传入字段被修正");
        assertEquals("fixed@x.com", resp.parsedEmail());
        assertEquals("13812348888", resp.parsedMobile(), "未传字段保持原值");
        assertEquals("RULE_LIMITED", resp.parseStatus(), "parse_status 保持不变");
        assertNull(resp.parseError());
    }

    @Test
    @DisplayName("update：无解析记录抛 NOT_FOUND")
    void updateNotFound() {
        when(parseMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class,
                () -> service.update(RESUME_ID, new ResumeParseUpdateRequest(null, null, null, null, null)));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }
}
