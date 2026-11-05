package com.hris.platform.controller;

import com.hris.platform.dto.InternalNotifyDtos.NotifyRequest;
import com.hris.platform.dto.InternalNotifyDtos.NotifyResponse;
import com.hris.platform.service.InternalNotifyService;
import com.hris.platform.service.TenantConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部端点契约（RCS-F-018 Offer 发送）：路径 /internal/plt/notify 与 data.status/error 字段。
 */
@ExtendWith(MockitoExtension.class)
class PlatformInternalControllerTest {

    @Mock
    private InternalNotifyService notifyService;
    @Mock
    private TenantConfigService configService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                new PlatformInternalController(notifyService, configService)).build();
    }

    private static final String BODY = """
            {"channel":"EMAIL","recipient":"hr@x.com","subject":"Offer",
             "content":"请查收","bizType":"RCS_OFFER","bizKey":"1"}
            """;

    @Test
    @DisplayName("POST /internal/plt/notify → data.status=SENT（招聘侧据此判成功）")
    void notifyReturnsRecruitContract() throws Exception {
        when(notifyService.notify(any(NotifyRequest.class)))
                .thenReturn(new NotifyResponse(true, "SENT", "EMAIL", "已发送", null, 200));

        mvc.perform(post("/internal/plt/notify")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.channel").value("EMAIL"))
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    @DisplayName("发送失败 → HTTP 200 + data.success=false + error，不返回 5xx")
    void failureStaysHttp200() throws Exception {
        when(notifyService.notify(any(NotifyRequest.class)))
                .thenReturn(new NotifyResponse(false, "FAILED", "EMAIL", "发送失败",
                        "渠道 EMAIL 未配置 webhook 地址", null));

        mvc.perform(post("/internal/plt/notify")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.error").value("渠道 EMAIL 未配置 webhook 地址"));
    }

    @Test
    @DisplayName("GET /internal/plt/tenant-configs?key=emp.no.pattern 返回租户模板")
    void tenantConfigLookup() throws Exception {
        when(configService.findValue("emp.no.pattern")).thenReturn("{PREFIX}{YYYY}{SEQ:4}");

        mvc.perform(get("/internal/plt/tenant-configs").param("key", "emp.no.pattern"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("{PREFIX}{YYYY}{SEQ:4}"));
    }
}
