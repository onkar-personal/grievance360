package com.grievance360.user;

import com.grievance360.audit.AuditService;
import com.grievance360.auth.AuthDtos;
import com.grievance360.auth.AuthService;
import com.grievance360.common.ApiException;
import com.grievance360.common.PageResponse;
import com.grievance360.common.Paging;
import com.grievance360.department.Department;
import com.grievance360.department.DepartmentRepository;
import com.grievance360.security.UserPrincipal;
import jakarta.persistence.criteria.JoinType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public record CreateUserRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @Pattern(regexp = AuthDtos.PHONE_RULE, message = "Enter a valid phone number") String phone,
            @NotNull Role role,
            Long departmentId,
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_RULE, message = AuthDtos.PASSWORD_MESSAGE) String password) {}

    public record UpdateUserRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @Pattern(regexp = AuthDtos.PHONE_RULE, message = "Enter a valid phone number") String phone,
            @NotNull Role role,
            Long departmentId,
            @NotNull Boolean active) {}

    public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_RULE, message = AuthDtos.PASSWORD_MESSAGE) String password) {}

    public record OfficerOption(Long id, String fullName, Long departmentId, String departmentName) {}

    @Transactional(readOnly = true)
    public PageResponse<UserDto> search(String q, Role role, Long departmentId, Boolean active,
                                        Integer page, Integer size, String sort) {
        Specification<User> spec = (r, cq, cb) -> {
            if (cq.getResultType() != Long.class && cq.getResultType() != long.class) r.fetch("department", JoinType.LEFT);
            return cb.conjunction();
        };
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((r, cq, cb) -> cb.or(cb.like(cb.lower(r.get("fullName")), like), cb.like(cb.lower(r.get("email")), like)));
        }
        if (role != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("role"), role));
        if (departmentId != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("department").get("id"), departmentId));
        if (active != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("active"), active));
        var pageable = Paging.of(page, size, sort, Set.of("createdAt", "fullName", "email", "role"), "createdAt,desc");
        return PageResponse.of(users.findAll(spec, pageable), UserDto::of);
    }

    @Transactional(readOnly = true)
    public List<OfficerOption> officers(Long departmentId) {
        List<User> list = departmentId == null ? users.findByRoleAndActiveTrue(Role.OFFICER)
                : users.findByRoleAndDepartmentIdAndActiveTrueOrderByFullName(Role.OFFICER, departmentId);
        return list.stream().map(u -> new OfficerOption(u.getId(), u.getFullName(),
                u.getDepartment() == null ? null : u.getDepartment().getId(),
                u.getDepartment() == null ? null : u.getDepartment().getName())).toList();
    }

    @Transactional
    public UserDto create(CreateUserRequest req, UserPrincipal actor) {
        String email = AuthService.normalizeEmail(req.email());
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("EMAIL_IN_USE", "An account with this email already exists");
        User u = new User();
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        u.setRole(req.role());
        u.setDepartment(resolveDepartment(req.role(), req.departmentId()));
        u.setPasswordHash(encoder.encode(req.password()));
        u.setActive(true);
        users.save(u);
        audit.record(actor, "USER_CREATED", "USER", u.getId(), "role=" + u.getRole());
        return UserDto.of(u);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest req, UserPrincipal actor) {
        User u = users.findWithDepartmentById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getId().equals(actor.id()) && (req.role() != Role.ADMIN || !req.active())) {
            throw ApiException.badRequest("SELF_LOCKOUT", "You cannot remove your own admin role or deactivate yourself");
        }
        Role oldRole = u.getRole();
        boolean oldActive = u.isActive();
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        u.setRole(req.role());
        u.setDepartment(resolveDepartment(req.role(), req.departmentId()));
        u.setActive(req.active());
        StringBuilder d = new StringBuilder();
        if (oldRole != u.getRole()) d.append("role ").append(oldRole).append("->").append(u.getRole()).append("; ");
        if (oldActive != u.isActive()) d.append("active ").append(oldActive).append("->").append(u.isActive()).append("; ");
        audit.record(actor, oldRole != u.getRole() ? "USER_ROLE_CHANGED" : "USER_UPDATED", "USER", u.getId(), d.toString());
        return UserDto.of(u);
    }

    @Transactional
    public void resetPassword(Long id, String password, UserPrincipal actor) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        u.setPasswordHash(encoder.encode(password));
        audit.record(actor, "USER_PASSWORD_RESET", "USER", u.getId(), null);
    }

    private Department resolveDepartment(Role role, Long departmentId) {
        if (role == Role.OFFICER) {
            if (departmentId == null) throw ApiException.badRequest("DEPARTMENT_REQUIRED", "Officers must belong to a department");
            return departments.findById(departmentId).orElseThrow(() -> ApiException.notFound("Department"));
        }
        if (role == Role.ADMIN && departmentId != null) {
            return departments.findById(departmentId).orElseThrow(() -> ApiException.notFound("Department"));
        }
        return null;
    }
}
