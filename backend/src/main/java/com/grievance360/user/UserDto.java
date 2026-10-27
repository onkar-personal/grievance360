package com.grievance360.user;

import java.time.Instant;

public record UserDto(Long id, String fullName, String email, String phone, Role role, Long departmentId,
                      String departmentName, String address, boolean active, Instant createdAt, Instant lastLoginAt) {
    public static UserDto of(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(),
                u.getDepartment() == null ? null : u.getDepartment().getId(),
                u.getDepartment() == null ? null : u.getDepartment().getName(),
                u.getAddress(), u.isActive(), u.getCreatedAt(), u.getLastLoginAt());
    }
}
