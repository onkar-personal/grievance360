package com.grievance360.department;

import com.grievance360.department.DepartmentDtos.*;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Departments & categories")
public class DepartmentController {
    private final DepartmentService service;

    @GetMapping("/departments")
    @Operation(summary = "Active departments (public)")
    public List<DepartmentDto> departments() {
        return service.listDepartments(false);
    }

    @GetMapping("/categories")
    @Operation(summary = "Active complaint categories (public)")
    public List<CategoryDto> categories() {
        return service.listCategories(false);
    }

    @GetMapping("/admin/departments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "All departments including inactive")
    public List<DepartmentDto> allDepartments() {
        return service.listDepartments(true);
    }

    @PostMapping("/admin/departments")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentDto createDepartment(@Valid @RequestBody DepartmentRequest req) {
        return service.createDepartment(req, CurrentUser.get());
    }

    @PutMapping("/admin/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentDto updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequest req) {
        return service.updateDepartment(id, req, CurrentUser.get());
    }

    @GetMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "All categories including inactive")
    public List<CategoryDto> allCategories() {
        return service.listCategories(true);
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest req) {
        return service.createCategory(req, CurrentUser.get());
    }

    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return service.updateCategory(id, req, CurrentUser.get());
    }
}
