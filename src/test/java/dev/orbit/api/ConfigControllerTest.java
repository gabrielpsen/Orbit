package dev.orbit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.orbit.security.ApiTokenFilter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ConfigControllerTest {
    private ConfigController controller(String prometheus, String grafana) {
        return new ConfigController("development", "scheduler", "localhost", 9090, 500, 30, 20, 5000, 10, 8, 350, 5000, 900,
                prometheus, grafana);
    }

    private MockHttpServletRequest admin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiTokenFilter.ADMIN_ATTRIBUTE, true);
        return request;
    }

    @Test
    void reportsTheConfiguredMonitoringUrls() {
        Map<String, Object> result = controller("https://prometheus.example.com", "https://grafana.example.com").config(admin());

        assertThat(result.get("endpoints")).isEqualTo(
                Map.of("prometheus", "https://prometheus.example.com", "grafana", "https://grafana.example.com"));
    }

    @Test
    void emptyUrlsAreReportedAsEmptySoTheUiCanHideThem() {
        Map<String, Object> result = controller("", "").config(admin());

        assertThat(result.get("endpoints")).isEqualTo(Map.of("prometheus", "", "grafana", ""));
    }

    @Test
    void configurationStaysAdminOnly() {
        assertThatThrownBy(() -> controller("", "").config(new MockHttpServletRequest()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
