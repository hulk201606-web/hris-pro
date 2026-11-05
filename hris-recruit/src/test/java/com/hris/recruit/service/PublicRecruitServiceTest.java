package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.config.RecruitProperties;
import com.hris.recruit.dto.PublicRecruitDtos.PublicApplyRequest;
import com.hris.recruit.dto.PublicRecruitDtos.PublicApplyResponse;
import com.hris.recruit.entity.Application;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.entity.Requisition;
import com.hris.recruit.entity.StageLog;
import com.hris.recruit.mapper.ApplicationMapper;
import com.hris.recruit.mapper.CandidateMapper;
import com.hris.recruit.mapper.JobOpeningMapper;
import com.hris.recruit.mapper.RequisitionMapper;
import com.hris.recruit.mapper.ResumeMapper;
import com.hris.recruit.mapper.StageLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PublicRecruitService 单元测试（RCS-F-004 / RCS-F-011）：
 * 幂等拒绝 / 关闭需求拒绝 / 落库字段（掩码+初始阶段）/ 黑名单命中拒绝与 fail-open 放行 /
 * selfIntro 白名单校验。租户上下文由控制器设置，此处聚焦业务语义。
 */
@ExtendWith(MockitoExtension.class)
class PublicRecruitServiceTest {

    private static final Long REQ_ID = 700001L;

    @Mock
    private RequisitionMapper requisitionMapper;
    @Mock
    private JobOpeningMapper jobOpeningMapper;
    @Mock
    private CandidateMapper candidateMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private StageLogMapper stageLogMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private BlacklistGuard blacklistGuard;

    private PublicRecruitService service;

    @BeforeEach
    void setUp() {
        service = new PublicRecruitService(requisitionMapper, jobOpeningMapper, candidateMapper,
                applicationMapper, stageLogMapper, resumeMapper, blacklistGuard, new RecruitProperties());
    }

    private PublicApplyRequest applyBody() {
        return new PublicApplyRequest("张三", "13812348888", "zhangsan@example.com", "五年 Java 后端开发经验");
    }

    private Requisition openRequisition() {
        Requisition r = new Requisition();
        r.setId(REQ_ID);
        r.setReqNo("R2026-100");
        r.setStatus(RequisitionService.STATUS_OPEN);
        r.setPublished(1);
        r.setDeptId(10L);
        r.setPositionId(20L);
        r.setHeadcount(2);
        return r;
    }

    private void mockInsertAssignIds() {
        when(candidateMapper.insert(any(Candidate.class))).thenAnswer(inv -> {
            Candidate c = inv.getArgument(0);
            c.setId(9001L);
            return 1;
        });
        when(applicationMapper.insert(any(Application.class))).thenAnswer(inv -> {
            Application a = inv.getArgument(0);
            a.setId(9002L);
            return 1;
        });
        when(stageLogMapper.insert(any(StageLog.class))).thenReturn(1);
        when(resumeMapper.insert(any(com.hris.recruit.entity.Resume.class))).thenReturn(1);
    }

    @Nested
    @DisplayName("apply 业务守卫")
    class Guards {

        @Test
        @DisplayName("需求不存在或非 OPEN → NOT_FOUND 通用文案，不落任何数据")
        void rejectsClosedOrMissingRequisition() {
            Requisition closed = openRequisition();
            closed.setStatus(RequisitionService.STATUS_CLOSED);
            when(requisitionMapper.selectById(REQ_ID)).thenReturn(closed);

            BizException e = assertThrows(BizException.class, () -> service.apply(REQ_ID, applyBody()));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
            verify(candidateMapper, never()).insert(any(Candidate.class));
        }

        @Test
        @DisplayName("幂等：同租户 email 已对该需求投递 → IDEMPOTENT_CONFLICT「已投递」")
        void rejectsDuplicateApply() {
            when(requisitionMapper.selectById(REQ_ID)).thenReturn(openRequisition());
            Candidate existing = new Candidate();
            existing.setId(8001L);
            existing.setEmail("zhangsan@example.com");
            when(candidateMapper.selectList(any())).thenReturn(List.of(existing));
            when(applicationMapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class, () -> service.apply(REQ_ID, applyBody()));

            assertEquals(ErrorCode.IDEMPOTENT_CONFLICT.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("已投递"));
            verify(candidateMapper, never()).insert(any(Candidate.class));
        }

        @Test
        @DisplayName("黑名单命中 → PARAM_INVALID 通用拒绝文案，不透露内部原因")
        void rejectsBlacklistedCandidate() {
            when(requisitionMapper.selectById(REQ_ID)).thenReturn(openRequisition());
            when(candidateMapper.selectList(any())).thenReturn(List.of());
            org.mockito.Mockito.doThrow(new BizException(ErrorCode.PARAM_INVALID, BlacklistGuard.HIT_MESSAGE))
                    .when(blacklistGuard).checkOrThrow(org.mockito.ArgumentMatchers.isNull(), eq("13812348888"));

            BizException e = assertThrows(BizException.class, () -> service.apply(REQ_ID, applyBody()));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertFalse(e.getMessage().contains("黑名单"), "公开链路不得透露内部原因");
            verify(candidateMapper, never()).insert(any(Candidate.class));
        }

        @Test
        @DisplayName("手机号格式非法 → PARAM_INVALID")
        void rejectsBadMobile() {
            BizException e = assertThrows(BizException.class, () -> service.apply(REQ_ID,
                    new PublicApplyRequest("张三", "12345", "z@x.com", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }

        @Test
        @DisplayName("selfIntro 含 emoji → PARAM_INVALID 白名单提示")
        void rejectsEmojiIntro() {
            BizException e = assertThrows(BizException.class, () -> service.apply(REQ_ID,
                    new PublicApplyRequest("张三", "13812348888", "z@x.com", "你好\uD83D\uDE00")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("emoji"));
        }

        @Test
        @DisplayName("selfIntro 纯标点空白（空洞文案）→ PARAM_INVALID")
        void rejectsHollowIntro() {
            BizException e = assertThrows(BizException.class, () -> service.apply(REQ_ID,
                    new PublicApplyRequest("张三", "13812348888", "z@x.com", "。。。 ！！！")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            assertTrue(e.getMessage().contains("仅支持"));
        }
    }

    @Nested
    @DisplayName("apply 落库字段")
    class Persistence {

        @Test
        @DisplayName("落库四件套：candidate(NEW+掩码)/application(APPLIED+ACTIVE)/stage_log(初始 APPLIED)/resume(selfIntro)")
        void persistsAllRecordsWithCorrectFields() {
            when(requisitionMapper.selectById(REQ_ID)).thenReturn(openRequisition());
            when(candidateMapper.selectList(any())).thenReturn(List.of());
            mockInsertAssignIds();

            PublicApplyResponse resp = service.apply(REQ_ID, applyBody());

            assertEquals(9002L, resp.applicationId());

            ArgumentCaptor<Candidate> cCap = ArgumentCaptor.forClass(Candidate.class);
            verify(candidateMapper).insert(cCap.capture());
            Candidate c = cCap.getValue();
            assertEquals("张三", c.getName());
            assertEquals("13812348888", c.getMobileCipher(), "mobile_cipher 语义与现有一致（明文落列）");
            assertEquals("138****8888", c.getMobileMask());
            assertEquals("zhangsan@example.com", c.getEmail());
            assertEquals(PublicRecruitService.CANDIDATE_NEW, c.getStatus());

            ArgumentCaptor<Application> aCap = ArgumentCaptor.forClass(Application.class);
            verify(applicationMapper).insert(aCap.capture());
            assertEquals(9001L, aCap.getValue().getCandidateId());
            assertEquals(REQ_ID, aCap.getValue().getOpeningId());
            assertEquals(PublicRecruitService.STAGE_APPLIED, aCap.getValue().getStage());
            assertEquals(PublicRecruitService.STATUS_ACTIVE, aCap.getValue().getStatus());

            ArgumentCaptor<StageLog> sCap = ArgumentCaptor.forClass(StageLog.class);
            verify(stageLogMapper).insert(sCap.capture());
            assertEquals(9002L, sCap.getValue().getApplicationId());
            assertNull(sCap.getValue().getFromStage(), "初始阶段 from_stage 为空");
            assertEquals(PublicRecruitService.STAGE_APPLIED, sCap.getValue().getToStage());
        }

        @Test
        @DisplayName("黑名单 fail-open：emp 不可用（guard 放行）→ 正常落库")
        void passesWhenBlacklistFailOpen() {
            when(requisitionMapper.selectById(REQ_ID)).thenReturn(openRequisition());
            when(candidateMapper.selectList(any())).thenReturn(List.of());
            mockInsertAssignIds();

            PublicApplyResponse resp = service.apply(REQ_ID, applyBody());

            assertEquals(9002L, resp.applicationId());
            verify(blacklistGuard).checkOrThrow(eq(null), eq("13812348888"));
        }

        @Test
        @DisplayName("selfIntro 为空时不写 resume")
        void skipsResumeWhenNoIntro() {
            when(requisitionMapper.selectById(REQ_ID)).thenReturn(openRequisition());
            when(candidateMapper.selectList(any())).thenReturn(List.of());
            when(candidateMapper.insert(any(Candidate.class))).thenAnswer(inv -> {
                Candidate c = inv.getArgument(0);
                c.setId(9001L);
                return 1;
            });
            when(applicationMapper.insert(any(Application.class))).thenReturn(1);
            when(stageLogMapper.insert(any(StageLog.class))).thenReturn(1);

            service.apply(REQ_ID, new PublicApplyRequest("张三", "13812348888", "z@x.com", null));

            verify(resumeMapper, never()).insert(any(com.hris.recruit.entity.Resume.class));
        }

        @Test
        @DisplayName("selfIntro 落简历时按 180 天缺省保留期计算 retentionUntil（RCS-F-022）")
        void setsResumeRetention() {
            when(requisitionMapper.selectById(REQ_ID)).thenReturn(openRequisition());
            when(candidateMapper.selectList(any())).thenReturn(List.of());
            mockInsertAssignIds();

            service.apply(REQ_ID, applyBody());

            ArgumentCaptor<com.hris.recruit.entity.Resume> cap =
                    ArgumentCaptor.forClass(com.hris.recruit.entity.Resume.class);
            verify(resumeMapper).insert(cap.capture());
            assertEquals(java.time.LocalDate.now().plusDays(180), cap.getValue().getRetentionUntil());
        }
    }

    @Nested
    @DisplayName("jobs 公开列表")
    class Jobs {

        @Test
        @DisplayName("仅返回 OPEN + published=1 的需求单，出参不含敏感字段")
        void listsOnlyOpenPublished() {
            Requisition r = openRequisition();
            when(requisitionMapper.selectList(any())).thenReturn(List.of(r));
            when(jobOpeningMapper.selectList(any())).thenReturn(List.of());

            List<com.hris.recruit.dto.PublicRecruitDtos.PublicJobItem> items = service.jobs();

            assertEquals(1, items.size());
            assertEquals(REQ_ID, items.get(0).reqId());
            assertEquals("R2026-100", items.get(0).reqNo());
            assertNull(items.get(0).title(), "无关联职位时 title 为 null");
        }
    }
}
