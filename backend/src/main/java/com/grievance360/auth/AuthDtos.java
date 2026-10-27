package com.grievance360.auth;

import com.grievance360.user.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-72 characters and contain a letter and a number";
    public static final String PHONE_RULE = "^$|^[+]?[0-9 ]{10,15}$";

    /** Registration never accepts a role: every self-registered account is a CITIZEN. */
    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @Pattern(regexp = PHONE_RULE, message = "Enter a valid phone number") String phone,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {}

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserDto user) {}
}
