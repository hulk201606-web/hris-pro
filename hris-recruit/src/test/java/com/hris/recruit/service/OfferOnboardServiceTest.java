package com.hris.recruit.service;

import com.hris.common.api.R;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.client.EmployeeClient;
import com.hris.recruit.entity.Application;
import com.hris.recruit.entity.Candidate;
import com.hris.recruit.entity.Offer;
import com.hris.recruit.mapper.ApplicationMapper;
import com.hris.recruit.mapper.CandidateMapper;
import com.hris.recruit.mapper.OfferMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OfferOnboardService 单元测试（RCU-F-030 offer→员工预建档跨域联动）。
 *
 * <p>锁定链路 offer → application → candidate → 员工域 pre-create，以及两道守卫：
 * 已录用拒重复建档（幂等）、任一前置实体缺失即 NOT_FOUND；跨域失败不回写 offer/candidate。</p>
 */
@ExtendWith(MockitoExtension.class)
class OfferOnboardServiceTest {

    @Mock
    private OfferMapper offerMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private CandidateMapper candidateMapper;
    @Mock
    private EmployeeClient employeeClient;

    private OfferOnboardService service;

    @BeforeEach
    void setUp() {
        service = new OfferOnboardService(offerMapper, applicationMapper, candidateMapper, employeeClient);
    }

    private Offer stubOffer(String status) {
        Offer o = new Offer();
        o.setId(900001L);
        o.setApplicationId(800001L);
        o.setStatus(status);
        return o;
    }

    private Application stubApplication() {
        Application a = new Application();
        a.setId(800001L);
        a.setCandidateId(700001L);
        return a;
    }

    private Candidate stubCandidate() {
        Candidate c = new Candidate();
        c.setId(700001L);
        c.setName("张三");
        c.setMobileCipher("CIPHER-MOBILE");
        c.setEmail("zhangsan@example.com");
        return c;
    }

    @Test
    @DisplayName("happy path：建档成功 → 返回 empId，offer=ACCEPTED、candidate=HIRED")
    void onboardsSuccessfully() {
        Offer offer = stubOffer("SENT");
        Candidate candidate = stubCandidate();
        when(offerMapper.selectById(900001L)).thenReturn(offer);
        when(applicationMapper.selectById(800001L)).thenReturn(stubApplication());
        when(candidateMapper.selectById(700001L)).thenReturn(candidate);
        when(employeeClient.preCreate(any())).thenReturn(R.ok(200500L));
        when(offerMapper.updateById(any(Offer.class))).thenReturn(1);
        when(candidateMapper.updateById(any(Candidate.class))).thenReturn(1);

        Long empId = service.acceptAndOnboard(900001L);

        assertEquals(200500L, empId);
        assertEquals("ACCEPTED", offer.getStatus());
        assertEquals("HIRED", candidate.getStatus());
        verify(offerMapper).updateById(offer);
        verify(candidateMapper).updateById(candidate);
    }

    @Test
    @DisplayName("建档请求携带候选人姓名/手机密文/邮箱与 RC 工号前缀")
    void preCreatePayloadCarriesCandidateData() {
        when(offerMapper.selectById(900001L)).thenReturn(stubOffer("SENT"));
        when(applicationMapper.selectById(800001L)).thenReturn(stubApplication());
        when(candidateMapper.selectById(700001L)).thenReturn(stubCandidate());
        when(employeeClient.preCreate(any())).thenReturn(R.ok(200500L));
        when(offerMapper.updateById(any(Offer.class))).thenReturn(1);
        when(candidateMapper.updateById(any(Candidate.class))).thenReturn(1);

        service.acceptAndOnboard(900001L);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(employeeClient).preCreate(captor.capture());
        assertEquals("张三", captor.getValue().get("name"));
        assertEquals("CIPHER-MOBILE", captor.getValue().get("mobile"));
        assertEquals("RC", captor.getValue().get("empNoPrefix"));
    }

    @Test
    @DisplayName("幂等：offer 已 ACCEPTED → PARAM_INVALID 且不调员工域")
    void rejectsDuplicateOnboard() {
        when(offerMapper.selectById(900001L)).thenReturn(stubOffer("ACCEPTED"));

        BizException e = assertThrows(BizException.class, () -> service.acceptAndOnboard(900001L));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(employeeClient, never()).preCreate(any());
    }

    @Test
    @DisplayName("Offer 不存在 → NOT_FOUND")
    void offerNotFound() {
        when(offerMapper.selectById(anyLong())).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.acceptAndOnboard(404L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("投递记录不存在 → NOT_FOUND（不建档）")
    void applicationNotFound() {
        when(offerMapper.selectById(900001L)).thenReturn(stubOffer("SENT"));
        when(applicationMapper.selectById(anyLong())).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.acceptAndOnboard(900001L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        verify(employeeClient, never()).preCreate(any());
    }

    @Test
    @DisplayName("候选人不存在 → NOT_FOUND（不建档）")
    void candidateNotFound() {
        when(offerMapper.selectById(900001L)).thenReturn(stubOffer("SENT"));
        when(applicationMapper.selectById(anyLong())).thenReturn(stubApplication());
        when(candidateMapper.selectById(anyLong())).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.acceptAndOnboard(900001L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        verify(employeeClient, never()).preCreate(any());
    }

    @Test
    @DisplayName("员工域建档失败 → RCS_ERROR，且不把 offer 标为已录用（避免半成功状态）")
    void onboardingFailureDoesNotMarkAccepted() {
        Offer offer = stubOffer("SENT");
        when(offerMapper.selectById(900001L)).thenReturn(offer);
        when(applicationMapper.selectById(anyLong())).thenReturn(stubApplication());
        when(candidateMapper.selectById(anyLong())).thenReturn(stubCandidate());
        when(employeeClient.preCreate(any())).thenReturn(R.fail(ErrorCode.EMP_ERROR));

        BizException e = assertThrows(BizException.class, () -> service.acceptAndOnboard(900001L));
        assertEquals(ErrorCode.RCS_ERROR.getCode(), e.getCode());
        assertEquals("SENT", offer.getStatus(), "跨域失败不应污染 offer 状态");
        verify(offerMapper, never()).updateById(any(Offer.class));
    }

    @Test
    @DisplayName("可录用态守卫：DRAFT → PARAM_INVALID 且不调员工域")
    void rejectsDraftOffer() {
        when(offerMapper.selectById(900001L)).thenReturn(stubOffer("DRAFT"));

        BizException e = assertThrows(BizException.class, () -> service.acceptAndOnboard(900001L));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(employeeClient, never()).preCreate(any());
    }

    @Test
    @DisplayName("可录用态守卫：REJECTED → PARAM_INVALID 且不调员工域")
    void rejectsRejectedOffer() {
        when(offerMapper.selectById(900001L)).thenReturn(stubOffer("REJECTED"));

        BizException e = assertThrows(BizException.class, () -> service.acceptAndOnboard(900001L));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(employeeClient, never()).preCreate(any());
    }
}
