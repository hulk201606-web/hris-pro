package com.hris.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * X-User-Perms 头解析单测（docs/21 批 7 #15 / D-11）：JSON 数组新格式 + 旧逗号串兼容。
 */
class AuthContextFilterTest {

    private final AuthContextFilter filter = new AuthContextFilter();

    @Test
    @DisplayName("新格式：JSON 数组解析为权限集合")
    void parsesJsonArray() {
        Set<String> perms = filter.parsePermsHeader("[\"emp:read\",\"cmp:salary:read\"]");
        assertEquals(Set.of("emp:read", "cmp:salary:read"), perms);
    }

    @Test
    @DisplayName("旧格式：逗号串仍可解析（灰度兼容）")
    void parsesLegacyCsv() {
        Set<String> perms = filter.parsePermsHeader("emp:read, cmp:salary:read ,org:write");
        assertEquals(Set.of("emp:read", "cmp:salary:read", "org:write"), perms);
    }

    @Test
    @DisplayName("空值/空白返回空集合")
    void blankReturnsEmpty() {
        assertEquals(Set.of(), filter.parsePermsHeader(null));
        assertEquals(Set.of(), filter.parsePermsHeader(""));
        assertEquals(Set.of(), filter.parsePermsHeader("  "));
    }

    @Test
    @DisplayName("JSON 数组内空白项被过滤")
    void jsonBlankItemsFiltered() {
        assertEquals(Set.of("emp:read"), filter.parsePermsHeader("[\"emp:read\", \"\"]"));
    }

    @Test
    @DisplayName("非法 JSON 回退逗号串解析（不抛异常）")
    void malformedJsonFallsBackToCsv() {
        assertEquals(Set.of("[abc"), filter.parsePermsHeader("[abc"));
    }

    @Test
    @DisplayName("端到端：过滤器把 JSON 数组头还原进 AuthContextHolder 并在 finally 清理")
    void endToEndFilterRestoresContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthContextFilter.HEADER_USER_ID, "42");
        request.addHeader(AuthContextFilter.HEADER_TENANT_ID, "7");
        request.addHeader(AuthContextFilter.HEADER_PERMS, "[\"emp:read\",\"cmp:salary:read\"]");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Set<String>> seen = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> seen.set(AuthContextHolder.getPerms()));
        assertEquals(Set.of("emp:read", "cmp:salary:read"), seen.get());
        Set<String> after = AuthContextHolder.getPerms();
        assertTrue(after == null || after.isEmpty(), "请求结束后 ThreadLocal 必须清理");
    }
}
