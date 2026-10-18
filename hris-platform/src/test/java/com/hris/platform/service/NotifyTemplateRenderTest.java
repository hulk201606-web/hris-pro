package com.hris.platform.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.NotificationDtos.RenderResponse;
import com.hris.platform.dto.NotificationDtos.TemplateRequest;
import com.hris.platform.entity.SysNotifyTemplate;
import com.hris.platform.mapper.SysNotifyTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotifyTemplateService 单元测试（PLT-F-003 通知模板集中渲染）。
 *
 * <p>核心是renderText 的占位符替换：{param} 逐个替换、参数值为 null 时替换为<b>空串</b>而非
 * 字面量 "null"（否则用户会收到 "您的验证码是 null" 这类短信）。</p>
 */
@ExtendWith(MockitoExtension.class)
class NotifyTemplateRenderTest {

    @Mock
    private SysNotifyTemplateMapper mapper;

    private NotifyTemplateService service;

    @BeforeEach
    void setUp() {
        service = new NotifyTemplateService(mapper);
    }

    private SysNotifyTemplate template(String subject, String content) {
        SysNotifyTemplate t = new SysNotifyTemplate();
        t.setId(1L);
        t.setCode("onboard_welcome");
        t.setChannel("SMS");
        t.setSubject(subject);
        t.setContent(content);
        return t;
    }

    @Nested
    @DisplayName("render：占位符替换")
    class Render {

        @Test
        @DisplayName("主题与正文均按 {key} 替换为参数值")
        void replacesPlaceholders() {
            when(mapper.selectOne(any())).thenReturn(template(
                    "欢迎 {name}", "您的工号是 {empNo}，部门 {dept}"));
            Map<String, Object> params = new HashMap<>();
            params.put("name", "张三");
            params.put("empNo", "E001");
            params.put("dept", "技术部");

            RenderResponse resp = service.render("onboard_welcome", "SMS", params);

            assertEquals("欢迎 张三", resp.subject());
            assertEquals("您的工号是 E001，部门 技术部", resp.content());
        }

        @Test
        @DisplayName("参数值为 null → 替换为空串，不产生字面量 \"null\"")
        void replacesNullParamWithEmptyString() {
            when(mapper.selectOne(any())).thenReturn(template("欢迎 {name}", "工号 {empNo}"));
            Map<String, Object> params = new HashMap<>();
            params.put("name", null);
            params.put("empNo", null);

            RenderResponse resp = service.render("onboard_welcome", "SMS", params);

            assertEquals("欢迎 ", resp.subject());
            assertFalse(resp.content().contains("null"),
                    "不得把 null 渲染到正文里，实际=" + resp.content());
        }

        @Test
        @DisplayName("模板含未被params 覆盖的占位符 → 原样保留，不被清空")
        void keepsUnmatchedPlaceholder() {
            when(mapper.selectOne(any())).thenReturn(template("主题", "工号 {empNo}"));
            Map<String, Object> params = new HashMap<>();
            params.put("other", "X");

            assertEquals("工号 {empNo}", service.render("onboard_welcome", "SMS", params).content(),
                    "未提供的占位符应原样保留，便于排查缺失参数");
        }

        @Test
        @DisplayName("params 为 null → 模板原样返回，不抛 NPE")
        void toleratesNullParams() {
            when(mapper.selectOne(any())).thenReturn(template("欢迎 {name}", "正文"));

            RenderResponse resp = service.render("onboard_welcome", "SMS", null);

            assertEquals("欢迎 {name}", resp.subject());
        }

        @Test
        @DisplayName("模板字段本身为 null → 渲染结果对应位为 null，不抛 NPE")
        void toleratesNullTemplateField() {
            when(mapper.selectOne(any())).thenReturn(template(null, null));

            RenderResponse resp = service.render("onboard_welcome", "SMS", Map.of("k", "v"));

            assertNull(resp.subject());
            assertNull(resp.content());
        }

        @Test
        @DisplayName("模板不存在 → NOT_FOUND")
        void templateNotFound() {
            when(mapper.selectOne(any())).thenReturn(null);

            BizException e = assertThrows(BizException.class,
                    () -> service.render("not_exist", "SMS", Map.of()));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("code 归一化为小写、channel 归一化为大写后再查模板（大小写不敏感命中）")
        void normalizesCodeAndChannel() {
            when(mapper.selectOne(any())).thenReturn(template("主题", "正文 {k}"));

            RenderResponse resp = service.render("  ONBOARD_WELCOME  ", " sms ",
                    Map.of("k", "V"));

            assertEquals("正文 V", resp.content());
            verify(mapper).selectOne(any());
        }
    }

    @Nested
    @DisplayName("create：同渠道编码唯一")
    class Create {

        @Test
        @DisplayName("同渠道下编码重复 → PARAM_INVALID，不落库")
        void rejectsDuplicateCodeInChannel() {
            when(mapper.selectCount(any())).thenReturn(1L);

            BizException e = assertThrows(BizException.class,
                    () -> service.create(new TemplateRequest("tpl", "SMS", "主题", "正文")));

            assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
            verify(mapper, never()).insert(any(SysNotifyTemplate.class));
        }

        @Test
        @DisplayName("编码归一化小写 + 渠道归一化大写后落库")
        void normalizesCodeAndChannelOnCreate() {
            when(mapper.selectCount(any())).thenReturn(0L);

            service.create(new TemplateRequest("  ONBOARD_WELCOME ", " sms ", "主题", "正文"));

            ArgumentCaptor<SysNotifyTemplate> captor =
                    ArgumentCaptor.forClass(SysNotifyTemplate.class);
            verify(mapper).insert(captor.capture());
            assertEquals("onboard_welcome", captor.getValue().getCode());
            assertEquals("SMS", captor.getValue().getChannel());
        }
    }
}