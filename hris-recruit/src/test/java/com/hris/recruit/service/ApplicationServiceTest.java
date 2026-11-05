package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.ApplicationDtos.ApplicationStageRequest;
import com.hris.recruit.dto.ApplicationDtos.ApplicationResponse;
import com.hris.recruit.entity.Application;
import com.hris.recruit.entity.StageLog;
import com.hris.recruit.mapper.ApplicationMapper;
import com.hris.recruit.mapper.StageLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ApplicationService 单元测试（RCS-F-012）：淘汰必填原因 / OTHER 需 note / 标签幂等。
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final Long APP_ID = 5001L;

    @Mock
    private ApplicationMapper mapper;
    @Mock
    private StageLogMapper stageLogMapper;
    @Mock
    private TalentPoolTagService tagService;

    private ApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(mapper, stageLogMapper, tagService);
    }

    private Application application() {
        Application e = new Application();
        e.setId(APP_ID);
        e.setCandidateId(9L);
        e.setOpeningId(20L);
        e.setStage("SCREEN");
        e.setStatus(ApplicationService.STATUS_ACTIVE);
        return e;
    }

    private ApplicationStageRequest reject(String reason, String note) {
        return new ApplicationStageRequest("REJECT", null, reason, note, null);
    }

    @Nested
    @DisplayName("淘汰原因")
    class Reject {

        @Test
        @DisplayName("淘汰缺少 reason → PARAM_INVALID，状态不迁移")
        void requiresReason() {
            when(mapper.selectById(APP_ID)).thenReturn(application());

            BizException e = assertThrows(BizException.class,
                    () -> service.advance(APP_ID, reject(null, null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Application.class));
            verify(stageLogMapper, never()).insert(any(StageLog.class));
        }

        @Test
        @DisplayName("OTHER 原因缺少 note → PARAM_INVALID")
        void otherRequiresNote() {
            when(mapper.selectById(APP_ID)).thenReturn(application());

            BizException e = assertThrows(BizException.class,
                    () -> service.advance(APP_ID, reject("OTHER", "  ")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).updateById(any(Application.class));
        }

        @Test
        @DisplayName("合法原因落 application.reason 并写阶段日志")
        void persistsReasonAndLog() {
            Application app = application();
            when(mapper.selectById(APP_ID)).thenReturn(app);
            when(mapper.updateById(any(Application.class))).thenReturn(1);

            ApplicationResponse resp = service.advance(APP_ID, reject("SKILL_MISMATCH", "Java 经验不足"));

            assertEquals(ApplicationService.STATUS_REJECTED, resp.status());
            assertEquals("SKILL_MISMATCH", resp.reason());
            assertEquals("Java 经验不足", resp.reasonNote());
            verify(stageLogMapper).insert(any(StageLog.class));
        }

        @Test
        @DisplayName("未知原因 → PARAM_INVALID")
        void rejectsUnknownReason() {
            when(mapper.selectById(APP_ID)).thenReturn(application());

            BizException e = assertThrows(BizException.class,
                    () -> service.advance(APP_ID, reject("NOT_A_REASON", null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }
    }

    @Nested
    @DisplayName("通过打标签")
    class Pass {

        @Test
        @DisplayName("通过可推进阶段，重复标签只打一次（幂等）")
        void tagsIdempotently() {
            Application app = application();
            when(mapper.selectById(APP_ID)).thenReturn(app);
            when(mapper.updateById(any(Application.class))).thenReturn(1);
            when(tagService.addIfAbsent(9L, "后端")).thenReturn(true);
            when(tagService.addIfAbsent(9L, "高潜")).thenReturn(false);

            ApplicationResponse resp = service.advance(APP_ID,
                    new ApplicationStageRequest("PASS", "INTERVIEW", null, null,
                            List.of("后端", "后端", " 高潜 ")));

            assertEquals("INTERVIEW", resp.stage());
            assertEquals(ApplicationService.STATUS_ACTIVE, resp.status());
            verify(tagService).addIfAbsent(9L, "后端");
            verify(tagService).addIfAbsent(9L, "高潜");
            verify(stageLogMapper).insert(any(StageLog.class));
        }

        @Test
        @DisplayName("非法 toStage → PARAM_INVALID")
        void rejectsInvalidStage() {
            when(mapper.selectById(APP_ID)).thenReturn(application());

            BizException e = assertThrows(BizException.class, () -> service.advance(APP_ID,
                    new ApplicationStageRequest("PASS", "HIRED_ELSEWHERE", null, null, null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }

        @Test
        @DisplayName("非法 action → PARAM_INVALID")
        void rejectsInvalidAction() {
            when(mapper.selectById(APP_ID)).thenReturn(application());

            BizException e = assertThrows(BizException.class, () -> service.advance(APP_ID,
                    new ApplicationStageRequest("HOLD", null, null, null, null)));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        }
    }
}
