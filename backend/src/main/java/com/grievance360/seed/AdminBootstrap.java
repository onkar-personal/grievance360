package com.grievance360.seed;

import com.grievance360.audit.AuditService;
import com.grievance360.auth.AuthDtos;
import com.grievance360.auth.AuthService;
import com.grievance360.config.AppProperties;
import com.grievance360.user.Role;
import com.grievance360.user.User;
import com.grievance360.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD, only when no active admin
 * exists. No default credentials are ever built in; unset the variables after the first successful start.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var b = props.bootstrap();
        if (b == null || b.adminEmail() == null || b.adminEmail().isBlank()) return;
        if (users.existsByRoleAndActiveTrue(Role.ADMIN)) {
            log.info("Admin bootstrap skipped: an administrator already exists. You can remove ADMIN_BOOTSTRAP_* variables.");
            return;
        }
        String password = b.adminPassword();
        if (password == null || !password.matches(AuthDtos.PASSWORD_RULE) || password.length() < 12) {
            log.error("Admin bootstrap skipped: ADMIN_BOOTSTRAP_PASSWORD must be at least 12 characters with letters and digits.");
            return;
        }
        String email = AuthService.normalizeEmail(b.adminEmail());
        if (users.existsByEmailIgnoreCase(email)) {
            log.error("Admin bootstrap skipped: a non-admin account already uses {}.", email);
            return;
        }
        User u = new User();
        u.setEmail(email);
        u.setFullName(b.adminName() == null || b.adminName().isBlank() ? "System Administrator" : b.adminName().trim());
        u.setPasswordHash(encoder.encode(password));
        u.setRole(Role.ADMIN);
        u.setActive(true);
        users.save(u);
        audit.record(null, "ADMIN_BOOTSTRAPPED", "USER", u.getId(), null);
        log.warn("Bootstrap administrator created for {}. Remove ADMIN_BOOTSTRAP_PASSWORD from the environment now.", email);
    }
}
