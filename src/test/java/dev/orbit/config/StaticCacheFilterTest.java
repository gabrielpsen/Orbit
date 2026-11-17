package dev.orbit.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class StaticCacheFilterTest {
    private final StaticCacheFilter filter = new StaticCacheFilter();

    private String cacheControl(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", path), response, new MockFilterChain());
        return response.getHeader("Cache-Control");
    }

    @Test
    void theHtmlEntryPointIsAlwaysRevalidated() throws Exception {
        assertThat(cacheControl("/")).isEqualTo("no-cache");
        assertThat(cacheControl("/index.html")).isEqualTo("no-cache");
        assertThat(cacheControl("/favicon.svg")).isEqualTo("no-cache");
    }

    @Test
    void fingerprintedAssetsAreCachedForAYear() throws Exception {
        assertThat(cacheControl("/assets/index-C6CmLT8S.js")).contains("max-age=31536000").contains("immutable");
    }

    @Test
    void apiResponsesAreLeftToTheirControllers() throws Exception {
        assertThat(cacheControl("/api/v1/stats")).isNull();
        assertThat(cacheControl("/actuator/health")).isNull();
    }
}
