package com.aix.api.config;

import com.aix.common.config.IngestProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class IngestAuthFilter extends OncePerRequestFilter {

    private final IngestProperties ingestProperties;

    public IngestAuthFilter(IngestProperties ingestProperties) {
        this.ingestProperties = ingestProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!ingestProperties.enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.startsWith("/api/ingest");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String configuredToken = ingestProperties.authToken();
        if (!StringUtils.hasText(configuredToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")
                && configuredToken.equals(authorization.substring(7))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"invalid ingest token\"}");
    }
}
