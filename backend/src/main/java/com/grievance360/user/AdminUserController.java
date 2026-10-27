package com.grievance360.user;

import com.grievance360.common.PageResponse;
import com.grievance360.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin: users")
public class AdminUserController {
    private final AdminUserService service;

    @GetMapping
    @Operation(summary = "Search users")
    public PageResponse<UserDto> search(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) Role role,
                                        @RequestParam(required = false) Long departmentId,
                                        @RequestParam(required = false) Boolean active,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size,
                                        @RequestParam(required = false) String sort) {
        return service.search(q, role, departmentId, active, page, size, sort);
    }

    @GetMapping("/officers")
    @Operation(summary = "Active officers, optionally for one department (for assignment pickers)")
    public List<AdminUserService.OfficerOption> officers(@RequestParam(required = false) Long departmentId) {
        return service.officers(departmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user with any role")
    public UserDto create(@Valid @RequestBody AdminUserService.CreateUserRequest req) {
        return service.create(req, CurrentUser.get());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user's details, role, department or active flag")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody AdminUserService.UpdateUserRequest req) {
        return service.update(id, req, CurrentUser.get());
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a new password for a user")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody AdminUserService.ResetPasswordRequest req) {
        service.resetPassword(id, req.password(), CurrentUser.get());
    }
}
