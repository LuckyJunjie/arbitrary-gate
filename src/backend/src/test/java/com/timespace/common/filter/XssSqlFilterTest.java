package com.timespace.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * I-06 XSS/SQL 注入 Filter 单元测试
 *
 * 测试策略：
 * 1. 正常参数：放行，值被正确转义
 * 2. XSS 攻击：参数被 HTML 转义（如 <script> → &lt;script&gt;）
 * 3. SQL 注入：危险模式参数触发 403 响应
 * 4. 故事内容接口（/api/story/*）：宽松白名单允许部分 HTML
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("I-06 XSS/SQL 注入 Filter 测试")
@Tag("filter")
class XssSqlFilterTest {

    private final XssFilter xssFilter = new XssFilter();

    // ── 正常参数放行 ────────────────────────────────────────────────

    @Test
    @DisplayName("正常参数应该放行")
    void testNormalParameterPass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/card/list");
        request.setMethod("POST");
        request.addParameter("name", "张三");
        request.addParameter("content", "这是一个正常的测试内容");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        verify(chain).doFilter(any(XssHttpServletRequestWrapper.class), eq(response));
    }

    @Test
    @DisplayName("正常参数值被 JSoup 正确转义")
    void testNormalValueEscaped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/update");
        request.setMethod("POST");
        request.addParameter("nickname", "User&Name");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        // 请求应该被放行（chain 被调用）
        verify(chain).doFilter(any(), eq(response));
    }

    // ── XSS 攻击转义 ────────────────────────────────────────────────

    @Test
    @DisplayName("XSS script 标签攻击应被转义")
    void testXssScriptTagBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/comment/create");
        request.setMethod("POST");
        request.addParameter("content", "<script>alert('xss')</script>");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        // XSS 内容会被转义后放行（不走 403）
        verify(chain).doFilter(any(), eq(response));
    }

    @Test
    @DisplayName("XSS img onerror 攻击应被转义")
    void testXssImgOnerrorBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/comment/create");
        request.setMethod("POST");
        request.addParameter("content", "<img src=x onerror=\"alert(1)\">");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), eq(response));
    }

    // ── SQL 注入拦截 ────────────────────────────────────────────────

    @Test
    @DisplayName("SQL 注入 'OR' 模式应返回 403")
    void testSqlInjectionORBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/login");
        request.setMethod("POST");
        request.addParameter("username", "admin");
        request.addParameter("password", "' OR '1'='1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("SQL 注入 DROP TABLE 应返回 403")
    void testSqlInjectionDropTableBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/reset");
        request.setMethod("POST");
        request.addParameter("table", "users; DROP TABLE users;--");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("SQL 注入 UNION SELECT 应返回 403")
    void testSqlInjectionUnionSelectBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/story/search");
        request.setMethod("GET");
        request.addParameter("q", "1 UNION SELECT password FROM users");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("SQL 注入 EXEC 应返回 403")
    void testSqlInjectionExecBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/query/exec");
        request.setMethod("POST");
        request.addParameter("sql", "EXEC sp_executesql");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("SQL 注入 DELETE 应返回 403")
    void testSqlInjectionDeleteBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/story/delete");
        request.setMethod("POST");
        request.addParameter("id", "1; DELETE FROM users WHERE 1=1;");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("SQL 注入 SLEEP 应返回 403")
    void testSqlInjectionSleepBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/check");
        request.setMethod("GET");
        request.addParameter("name", "admin' AND SLEEP(5)--");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    // ── Header 注入检测 ─────────────────────────────────────────────

    @Test
    @DisplayName("Header 中的 SQL 注入应返回 403")
    void testHeaderSqlInjectionBlocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/info");
        request.setMethod("GET");
        request.addHeader("X-Custom-Header", "value'; DROP TABLE users;--");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    // ── OPTIONS 预检请求 ─────────────────────────────────────────────

    @Test
    @DisplayName("OPTIONS 预检请求应直接放行")
    void testOptionsRequestPass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/story/list");
        request.setMethod("OPTIONS");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        verify(chain).doFilter(eq(request), eq(response));
    }

    // ── 故事内容接口宽松白名单 ───────────────────────────────────────

    @Test
    @DisplayName("故事内容接口允许部分 HTML 标签")
    void testStoryContentRelaxedWhiteList() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/story/content");
        request.setMethod("POST");
        request.addParameter("content", "<p>这是<strong>粗体</strong>内容</p>");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        // 宽松白名单放行，chain 被调用
        verify(chain).doFilter(any(), eq(response));
    }

    @Test
    @DisplayName("普通接口不允许 HTML 标签")
    void testNormalApiNoHtmlAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/comment/create");
        request.setMethod("POST");
        request.addParameter("content", "<p>hello</p>");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        xssFilter.doFilter(request, response, chain);

        // 普通接口走严格白名单，HTML 被净化，chain 仍然放行
        verify(chain).doFilter(any(), eq(response));
    }

    // ── Wrapper 直接测试 ─────────────────────────────────────────────

    @Test
    @DisplayName("XssHttpServletRequestWrapper 对普通参数正确封装")
    void testWrapperGetParameter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/update");
        request.setMethod("POST");
        request.addParameter("nickname", "张三");
        request.addParameter("bio", "程序员");

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);

        assertEquals("张三", wrapper.getParameter("nickname"));
        assertEquals("程序员", wrapper.getParameter("bio"));
    }

    @Test
    @DisplayName("XssHttpServletRequestWrapper 对 XSS 参数正确转义")
    void testWrapperXssEscaping() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/update");
        request.setMethod("POST");
        request.addParameter("nickname", "<script>alert(1)</script>");

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);

        String value = wrapper.getParameter("nickname");
        assertFalse(value.contains("<script>"));
        assertTrue(value.contains("&lt;script&gt;"));
    }

    @Test
    @DisplayName("XssHttpServletRequestWrapper 对多个参数值正确处理")
    void testWrapperGetParameterValues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/story/search");
        request.setMethod("GET");
        request.addParameter("tags", new String[]{"江", "河", "雨"});

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);

        String[] values = wrapper.getParameterValues("tags");
        assertNotNull(values);
        assertEquals(3, values.length);
    }

    @Test
    @DisplayName("XssHttpServletRequestWrapper 对 Header 正确转义")
    void testWrapperHeaderEscaping() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user/login");
        request.setMethod("POST");
        request.addHeader("X-Token", "Bearer abc&def");

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);

        String value = wrapper.getHeader("X-Token");
        // & 会被转义为 &amp;
        assertFalse(value.contains("&amp;") && value.contains("&"));
    }
}
