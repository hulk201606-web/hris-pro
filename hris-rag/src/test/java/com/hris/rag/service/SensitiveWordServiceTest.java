package com.hris.rag.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.dto.SensitiveWordDtos.SensitiveWordCreateRequest;
import com.hris.rag.entity.SensitiveWord;
import com.hris.rag.mapper.SensitiveWordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SensitiveWordService 单元测试（KBD-F-017）：BLOCK 拒绝 / WARN 放行 / 无词放行。
 */
@ExtendWith(MockitoExtension.class)
class SensitiveWordServiceTest {

    @Mock
    private SensitiveWordMapper mapper;

    private SensitiveWordService service;

    @BeforeEach
    void setUp() {
        service = new SensitiveWordService(mapper);
    }

    private SensitiveWord word(String word, String level) {
        SensitiveWord w = new SensitiveWord();
        w.setWord(word);
        w.setLevel(level);
        return w;
    }

    @Test
    @DisplayName("BLOCK 命中 → PARAM_INVALID「内容包含敏感词：{词}」")
    void blocksOnHit() {
        when(mapper.selectList(any())).thenReturn(List.of(word("赌博", "BLOCK")));

        BizException e = assertThrows(BizException.class,
                () -> service.scan("本章讨论赌博的危害"));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("赌博"));
    }

    @Test
    @DisplayName("WARN 命中 → 放行并返回警告词列表")
    void warnsButPasses() {
        when(mapper.selectList(any())).thenReturn(List.of(word("内部未公开", "WARN")));

        List<String> warnings = service.scan("内部未公开的调整方案");

        assertEquals(List.of("内部未公开"), warnings);
    }

    @Test
    @DisplayName("无词命中 → 空列表，不误伤")
    void passesCleanText() {
        when(mapper.selectList(any())).thenReturn(List.of(word("赌博", "BLOCK")));

        assertTrue(service.scan("正常的员工手册内容").isEmpty());
    }

    @Test
    @DisplayName("空词库 → 直接放行，不做扫描")
    void passesWhenNoWords() {
        when(mapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.scan("任意内容").isEmpty());
    }

    @Test
    @DisplayName("新增缺省 WARN；重复词拒绝")
    void createDefaultsWarnAndRejectsDuplicate() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(SensitiveWord.class))).thenAnswer(inv -> {
            SensitiveWord w = inv.getArgument(0);
            w.setId(1L);
            return 1;
        });

        assertEquals("WARN", service.create(new SensitiveWordCreateRequest("测试词", null)).level());

        when(mapper.selectCount(any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class,
                () -> service.create(new SensitiveWordCreateRequest("测试词", "BLOCK")));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("非法级别 → PARAM_INVALID，不落库")
    void rejectsInvalidLevel() {
        BizException e = assertThrows(BizException.class,
                () -> service.create(new SensitiveWordCreateRequest("测试词", "INFO")));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(mapper, never()).insert(any(SensitiveWord.class));
    }
}
