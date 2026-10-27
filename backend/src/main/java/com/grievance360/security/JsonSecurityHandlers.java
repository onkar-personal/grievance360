package com.grievance360.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grievance360.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Writes consistent JSON bodies for 401 and 403 responses produced by the security filter chain. */
@Component
@RequiredArgsConstructor
public class JsonSecurityHandlers {
    private final ObjectMapper mapper;

    public AuthenticationEntryPoint entryPoint() {
        return (req, res, ex) -> write(req, res, 401, "Unauthorized", "UNAUTHORIZED", "Authentication required");
    }

    public AccessDeniedHandler accessDenied() {
        return (req, res, ex) -> write(req, res, 403, "Forbidden", "FORBIDDEN", "You do not have permission to perform this action");
    }

    private void write(HttpServletRequest req, HttpServletResponse res, int status, String error, String code, String msg)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ApiError.of(status, error, code, msg, req.getRequestURI()));
    }
}
