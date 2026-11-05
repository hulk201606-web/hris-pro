package com.hris.recruit.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.CandidateDtos.CandidateCreateRequest;
import com.hris.recruit.dto.CandidateDtos.CandidateResponse;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.mapper.CandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CandidateService 单元测试（docs/19 §8.7-3：响应侧手机号掩码，明文不出候选人域）。
 */
@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    private CandidateMapper mapper;

    @Mock
    private BlacklistGuard blacklistGuard;

    private CandidateService service;

    @BeforeEach
    void setUp() {
        service = new CandidateService(mapper, blacklistGuard);
    }

    @Test
    @DisplayName("create 返回值：mobileMask 为掩码，响应不携带手机号明文")
    void createResponseMasksMobile() {
        when(mapper.insert(any(Candidate.class))).thenAnswer(inv -> {
            Candidate c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });

        CandidateResponse resp = service.create(new CandidateCreateRequest(
                "张三", "13812348888", "138****8888", "z@x.com", "NEW"));

        assertEquals("138****8888", resp.mobileMask());
        assertFalse(String.valueOf(resp).contains("13812348888"), "响应不得包含手机号明文");
    }

    @Test
    @DisplayName("create 入参未传 mobileMask 时，落库掩码由服务侧按原文生成补齐")
    void createFillsMaskFromPlain() {
        when(mapper.insert(any(Candidate.class))).thenAnswer(inv -> {
            Candidate c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });

        service.create(new CandidateCreateRequest(
                "张三", "13812348888", null, "z@x.com", "NEW"));

        ArgumentCaptor<Candidate> captor = ArgumentCaptor.forClass(Candidate.class);
        verify(mapper).insert(captor.capture());
        assertEquals("138****8888", captor.getValue().getMobileMask(),
                "创建时应按原文生成掩码影子列，避免 mask 依赖调用方");
    }

    @Test
    @DisplayName("get：不存在抛 NOT_FOUND")
    void getNotFound() {
        when(mapper.selectById(404L)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.get(404L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }
}
