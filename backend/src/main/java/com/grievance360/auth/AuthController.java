package com.grievance360.auth;

import com.grievance360.auth.AuthDtos.AuthResponse;
import com.grievance360.auth.AuthDtos.LoginRequest;
import com.grievance360.auth.AuthDtos.RegisterRequest;
import com.grievance360.common.ApiException;
import com.grievance360.security.CurrentUser;
import com.grievance360.user.UserDto;
import com.grievance360.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService auth;
    private final UserRepository users;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a citizen account")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a bearer access token")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user")
    @Transactional(readOnly = true)
    public UserDto me() {
        return users.findWithDepartmentById(CurrentUser.get().id()).map(UserDto::of)
                .orElseThrow(() -> ApiException.notFound("User"));
    }
}
