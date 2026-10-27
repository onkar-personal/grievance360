package com.grievance360.user;

import com.grievance360.audit.AuditService;
import com.grievance360.auth.AuthDtos;
import com.grievance360.common.ApiException;
import com.grievance360.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "Profile")
public class ProfileController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @Pattern(regexp = AuthDtos.PHONE_RULE, message = "Enter a valid phone number") String phone,
            @Size(max = 300) String address) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_RULE, message = AuthDtos.PASSWORD_MESSAGE) String newPassword) {}

    @GetMapping
    @Operation(summary = "Get own profile")
    @Transactional(readOnly = true)
    public UserDto get() {
        return UserDto.of(load());
    }

    @PutMapping
    @Operation(summary = "Update own profile (name, phone, address only)")
    @Transactional
    public UserDto update(@Valid @RequestBody UpdateProfileRequest req) {
        User u = load();
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        u.setAddress(req.address() == null || req.address().isBlank() ? null : req.address().trim());
        return UserDto.of(u);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change own password")
    @Transactional
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        User u = load();
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Current password is incorrect");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        audit.record(CurrentUser.get(), "PASSWORD_CHANGED", "USER", u.getId(), null);
    }

    private User load() {
        return users.findWithDepartmentById(CurrentUser.get().id()).orElseThrow(() -> ApiException.notFound("User"));
    }
}
