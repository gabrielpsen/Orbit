package dev.orbit.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Revalidates the HTML and caches hashed assets. Files inside the jar are dated 1980. */
@Component
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class StaticCacheFilter extends OncePerRequestFilter {
    static final String REVALIDATE = "no-cache";
    static final String IMMUTABLE = "public, max-age=31536000, immutable";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/assets/")) {
            response.setHeader("Cache-Control", IMMUTABLE);
        } else if (!path.startsWith("/api/") && !path.startsWith("/actuator/")) {
            response.setHeader("Cache-Control", REVALIDATE);
        }
        filterChain.doFilter(request, response);
    }
}
