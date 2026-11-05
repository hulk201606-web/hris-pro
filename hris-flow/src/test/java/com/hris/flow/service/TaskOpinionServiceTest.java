package com.hris.flow.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.TaskExtDtos.OpinionTagRequest;
import com.hris.flow.dto.TaskExtDtos.OpinionTagView;
import com.hris.flow.entity.TaskOpinionTag;
import com.hris.flow.mapper.TaskOpinionTagMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结构化意见标签单测（FLW-F-020）：枚举校验、留痕字段、随审批动作内联打标、长度约束。
 */
@ExtendWith(MockitoExtension.class)
class TaskOpinionServiceTest {

    private static final String TASK_ID = "t1";

    @Mock
    TaskOpinionTagMapper opinionTagMapper;
    @Mock
    TaskGuard taskGuard;

    private TaskOpinionService service;

    @BeforeEach
    void setUp() {
        service = new TaskOpinionService(opinionTagMapper, taskGuard);
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(5L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("合法标签 → 落库（大写归一 + 打标人 = 当前用户）")
    void shouldInsertValidTag() {
        when(taskGuard.requireWritableTask(TASK_ID)).thenReturn(null);

        OpinionTagView view = service.tag(TASK_ID, new OpinionTagRequest("compliant", " 资料齐全 "));

        ArgumentCaptor<TaskOpinionTag> captor = ArgumentCaptor.forClass(TaskOpinionTag.class);
        verify(opinionTagMapper).insert(captor.capture());
        assertEquals("COMPLIANT", captor.getValue().getTag());
        assertEquals("资料齐全", captor.getValue().getComment());
        assertEquals(5L, captor.getValue().getOperator());
        assertEquals("COMPLIANT", view.tag());
    }

    @Test
    @DisplayName("非法标签取值 → 10001，不落库")
    void shouldRejectIllegalTag() {
        when(taskGuard.requireWritableTask(TASK_ID)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.tag(TASK_ID, new OpinionTagRequest("MAYBE", null)));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(opinionTagMapper, never()).insert(any(TaskOpinionTag.class));
    }

    @Test
    @DisplayName("空标签 → 10001")
    void shouldRejectBlankTag() {
        when(taskGuard.requireWritableTask(TASK_ID)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.tag(TASK_ID, new OpinionTagRequest("", null)));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("审批动作内联打标：tag 为空静默跳过；有 tag 则按动作留痕")
    void shouldRecordTagOnlyWhenProvided() {
        service.recordForAction(TASK_ID, null, "同意");
        verify(opinionTagMapper, never()).insert(any(TaskOpinionTag.class));

        service.recordForAction(TASK_ID, "RECHECK_DATA", "请复核 3 月考勤");

        ArgumentCaptor<TaskOpinionTag> captor = ArgumentCaptor.forClass(TaskOpinionTag.class);
        verify(opinionTagMapper).insert(captor.capture());
        assertEquals("RECHECK_DATA", captor.getValue().getTag());
        assertEquals(TASK_ID, captor.getValue().getTaskId());
    }

    @Test
    @DisplayName("说明超长（&gt;512）→ 10001")
    void shouldRejectTooLongComment() {
        when(taskGuard.requireWritableTask(TASK_ID)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.tag(TASK_ID, new OpinionTagRequest("OTHER", "x".repeat(513))));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("标签列表映射视图；空说明归一为 null")
    void shouldListTags() {
        TaskOpinionTag row = new TaskOpinionTag();
        row.setId(1L);
        row.setTaskId(TASK_ID);
        row.setTag("NEED_MORE_INFO");
        row.setComment(null);
        when(opinionTagMapper.selectList(any())).thenReturn(List.of(row));

        List<OpinionTagView> views = service.list(TASK_ID);

        assertEquals(1, views.size());
        assertEquals("NEED_MORE_INFO", views.get(0).tag());
        assertNull(views.get(0).comment());
    }
}
