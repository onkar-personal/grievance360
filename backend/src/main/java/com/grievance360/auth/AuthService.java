package com.grievance360.auth;

import com.grievance360.audit.AuditService;
import com.grievance360.auth.AuthDtos.AuthResponse;
import com.grievance360.auth.AuthDtos.LoginRequest;
import com.grievance360.auth.AuthDtos.RegisterRequest;
import com.grievance360.common.ApiException;
import com.grievance360.common.RequestContext;
import com.grievance360.security.JwtService;
import com.grievance360.security.RateLimiter;
import com.grievance360.security.UserPrincipal;
import com.grievance360.user.Role;
import com.grievance360.user.User;
import com.grievance360.user.UserDto;
import com.grievance360.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RateLimiter rateLimiter;
    private final AuditService audit;

    // Hash of a random string; compared against for unknown emails so login timing does not reveal account existence.
    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
        return dummyHash;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        rateLimiter.check("register:" + RequestContext.clientIp());
        String email = normalizeEmail(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "An account with this email already exists");
        }
        User u = new User();
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPhone(blankToNull(req.phone()));
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.CITIZEN);
        u.setActive(true);
        u.setLastLoginAt(Instant.now());
        users.save(u);
        audit.record(UserPrincipal.of(u), "USER_REGISTERED", "USER", u.getId(), null);
        return toResponse(u);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        String key = "login:" + RequestContext.clientIp() + ":" + email;
        rateLimiter.check(key);
        User u = users.findByEmailIgnoreCase(email).orElse(null);
        boolean ok = encoder.matches(req.password(), u == null ? dummyHash() : u.getPasswordHash());
        if (u == null || !ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        if (!u.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been deactivated");
        }
        rateLimiter.reset(key);
        u.setLastLoginAt(Instant.now());
        return toResponse(u);
    }

    private AuthResponse toResponse(User u) {
        JwtService.IssuedToken t = jwt.issue(u);
        return new AuthResponse(t.token(), "Bearer", t.expiresAt(), UserDto.of(u));
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
