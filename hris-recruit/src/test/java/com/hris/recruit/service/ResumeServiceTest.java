package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.config.RecruitProperties;
import com.hris.recruit.dto.ResumeDtos.ResumeCreateRequest;
import com.hris.recruit.dto.ResumeDtos.ResumeResponse;
import com.hris.recruit.entity.Resume;
import com.hris.recruit.mapper.ResumeMapper;
import com.hris.recruit.mapper.ResumeParseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResumeService 单元测试（RCS-F-022）：保留期计算 / 过期清理筛选 / 候选人删除联动。
 */
@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private ResumeMapper mapper;
    @Mock
    private ResumeParseMapper parseMapper;

    private RecruitProperties properties;
    private ResumeService service;

    @BeforeEach
    void setUp() {
        properties = new RecruitProperties();
        service = new ResumeService(mapper, parseMapper, properties);
    }

    private Resume resume(Long id) {
        Resume r = new Resume();
        r.setId(id);
        r.setCandidateId(9L);
        r.setFileId(88L);
        r.setParseResult("{\"name\":\"张三\"}");
        return r;
    }

    @Nested
    @DisplayName("保留期计算")
    class Retention {

        @Test
        @DisplayName("缺省 180 天：retentionUntil = today + 180")
        void defaultsTo180Days() {
            when(mapper.insert(any(Resume.class))).thenReturn(1);

            ResumeResponse resp = service.create(new ResumeCreateRequest(9L, 88L, null));

            assertEquals(LocalDate.now().plusDays(180), resp.retentionUntil());
        }

        @Test
        @DisplayName("租户配置生效：hris.recruit.resume-retention-days=30 → today + 30")
        void honorsConfiguredDays() {
            properties.setResumeRetentionDays(30);
            when(mapper.insert(any(Resume.class))).thenReturn(1);

            ResumeResponse resp = service.create(new ResumeCreateRequest(9L, 88L, null));

            assertEquals(LocalDate.now().plusDays(30), resp.retentionUntil());
        }
    }

    @Nested
    @DisplayName("过期清理")
    class Cleanup {

        @Test
        @DisplayName("清理筛选：仅 retention_until < today 的记录被清空引用并逻辑删除")
        void cleansExpiredOnly() {
            when(mapper.selectList(any())).thenReturn(List.of(resume(5L)));
            when(mapper.delete(any())).thenReturn(1);

            int cleaned = service.cleanupExpired();

            assertEquals(1, cleaned);
            verify(mapper).update(isNull(), any());
            verify(mapper).delete(any());
            verify(parseMapper).delete(any());
        }

        @Test
        @DisplayName("无过期记录 → cleaned=0，不产生写操作")
        void noExpiredNoWrite() {
            when(mapper.selectList(any())).thenReturn(List.of());

            assertEquals(0, service.cleanupExpired());
            verify(mapper, never()).delete(any());
            verify(parseMapper, never()).delete(any());
        }
    }

    @Test
    @DisplayName("候选人删除联动：返回名下简历明细并清理关联解析")
    void deletesByCandidate() {
        when(mapper.selectList(any())).thenReturn(List.of(resume(5L), resume(6L)));
        when(mapper.delete(any())).thenReturn(2);

        List<Long> ids = service.deleteByCandidate(9L);

        assertEquals(List.of(5L, 6L), ids);
        verify(mapper).update(isNull(), any());
        verify(mapper).delete(any());
        verify(parseMapper).delete(any());
    }

    @Test
    @DisplayName("get：不存在抛 NOT_FOUND")
    void getNotFound() {
        when(mapper.selectById(404L)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.get(404L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }
}
