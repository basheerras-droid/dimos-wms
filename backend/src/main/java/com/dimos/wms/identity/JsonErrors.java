package com.dimos.wms.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;

/** Small JSON error bodies written from security filters (outside Spring MVC). Only fixed text is used. */
final class JsonErrors {

    private JsonErrors() {
    }

    static void forbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, 403, "FORBIDDEN", "You are not allowed to do this.");
    }

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        String correlation = MDC.get("correlationId"); // validated by CorrelationIdFilter, safe to embed
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"correlationId\":" + (correlation == null ? "null" : "\"" + correlation + "\"") + "}");
    }
}
