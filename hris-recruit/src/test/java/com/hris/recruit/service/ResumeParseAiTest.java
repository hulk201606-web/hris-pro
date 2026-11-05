package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hris.common.exception.BizException;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseRequest;
import com.hris.recruit.dto.ResumeParseDtos.ResumeParseResponse;
import com.hris.recruit.entity.Candidate;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResumeParseService AI 模式单元测试（批 6 #9）：AI_PARSED 落库 / 失败不覆盖旧结果 /
 * 回填复用（空字段写入，人工数据不覆盖）。
 */
@ExtendWith(MockitoExtension.class)
class ResumeParseAiTest {

    private static final Long RESUME_ID = 100L;
    private static final Long CANDIDATE_ID = 200L;

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeParseMapper parseMapper;
    @Mock
    private CandidateMapper candidateMapper;
    @Mock
    private DashScopeResumeAiClient aiClient;

    private ResumeParseService service;
    private Resume resume;
    private Candidate candidate;

    @BeforeAll
    static void initTableInfo() {
        // 纯 Mockito 环境无 MP 运行时，手动注册实体 lambda 缓存（LambdaQueryWrapper 解析列名依赖）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ResumeParse.class);
    }

    @BeforeEach
    void setUp() {
        service = new ResumeParseService(resumeMapper, parseMapper, candidateMapper, aiClient);
        resume = new Resume();
        resume.setId(RESUME_ID);
        resume.setCandidateId(CANDIDATE_ID);
        candidate = new Candidate();
        candidate.setId(CANDIDATE_ID);
        lenient().when(resumeMapper.selectById(RESUME_ID)).thenReturn(resume);
        lenient().when(candidateMapper.selectById(CANDIDATE_ID)).thenReturn(candidate);
    }

    private ResumeParseRequest req() {
        return new ResumeParseRequest("张三 13812348888 zhangsan@x.com");
    }

    @Test
    @DisplayName("mode=ai：LLM 抽取结果落库，parse_status=AI_PARSED，education/experience 为 LLM 结构")
    void aiParsePersistsAiParsed() {
        when(parseMapper.selectOne(any())).thenReturn(null);
        when(aiClient.extract(req().text())).thenReturn(new DashScopeResumeAiClient.AiExtraction(
                "张三", "13812348888", "zhangsan@x.com",
                "[{\"school\":\"X大\",\"major\":\"CS\"}]",
                "[{\"company\":\"Y厂\",\"title\":\"后端\"}]"));

        ResumeParseResponse resp = service.parse(RESUME_ID, "ai", req());

        assertEquals("AI_PARSED", resp.parseStatus());
        assertEquals("张三", resp.parsedName());
        assertEquals("[{\"school\":\"X大\",\"major\":\"CS\"}]", resp.educationJson());
        assertEquals("[{\"company\":\"Y厂\",\"title\":\"后端\"}]", resp.experienceJson());
        verify(parseMapper).insert(any(com.hris.recruit.entity.ResumeParse.class));
    }

    @Test
    @DisplayName("mode=ai：LLM 失败 → 报错注明 AI 解析失败，旧解析结果保持不变（不覆盖）")
    void aiFailureKeepsOldResult() {
        when(aiClient.extract(any())).thenThrow(new BizException(
                com.hris.common.exception.ErrorCode.RCS_ERROR, "AI 解析失败：LLM 调用异常（timeout）"));

        BizException e = assertThrows(BizException.class,
                () -> service.parse(RESUME_ID, "ai", req()));

        assertTrue(e.getMessage().contains("AI 解析失败"));
        verify(parseMapper, never()).updateById(any(com.hris.recruit.entity.ResumeParse.class));
        verify(parseMapper, never()).insert(any(com.hris.recruit.entity.ResumeParse.class));
    }

    @Test
    @DisplayName("mode=ai：回填 candidate 仅写空缺字段，人工数据不覆盖（语义复用）")
    void aiParseBackfillsOnlyBlanks() {
        when(parseMapper.selectOne(any())).thenReturn(null);
        candidate.setName("人工录入名");
        candidate.setMobileCipher(null);
        when(aiClient.extract(any())).thenReturn(new DashScopeResumeAiClient.AiExtraction(
                "AI名", "13812348888", "ai@x.com", null, null));

        service.parse(RESUME_ID, "ai", req());

        ArgumentCaptor<Candidate> cap = ArgumentCaptor.forClass(Candidate.class);
        verify(candidateMapper).updateById(cap.capture());
        assertEquals("人工录入名", cap.getValue().getName(), "人工数据不覆盖");
        assertEquals("13812348888", cap.getValue().getMobileCipher(), "空缺字段回填");
        assertEquals("138****8888", cap.getValue().getMobileMask());
    }

    @Test
    @DisplayName("mode 缺省（rule）：仍走规则解析，status=RULE_LIMITED（兼容）")
    void defaultModeStaysRule() {
        when(parseMapper.selectOne(any())).thenReturn(null);

        ResumeParseResponse resp = service.parse(RESUME_ID, "rule", req());

        assertEquals("RULE_LIMITED", resp.parseStatus());
        verify(aiClient, never()).extract(any());
    }
}
