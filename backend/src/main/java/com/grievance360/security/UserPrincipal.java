package com.grievance360.security;

import com.grievance360.user.Role;
import com.grievance360.user.User;

/** Authenticated caller, resolved from the database on every request so role/active changes apply immediately. */
public record UserPrincipal(Long id, String email, String fullName, Role role, Long departmentId) {
    public static UserPrincipal of(User u) {
        return new UserPrincipal(u.getId(), u.getEmail(), u.getFullName(), u.getRole(),
                u.getDepartment() == null ? null : u.getDepartment().getId());
    }

    public boolean is(Role r) {
        return role == r;
    }
}
