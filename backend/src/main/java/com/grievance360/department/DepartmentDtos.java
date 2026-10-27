package com.grievance360.department;

import com.grievance360.complaint.Priority;
import jakarta.validation.constraints.*;

public final class DepartmentDtos {
    private DepartmentDtos() {}

    public record DepartmentDto(Long id, String code, String name, String description, String contactEmail, boolean active) {
        public static DepartmentDto of(Department d) {
            return new DepartmentDto(d.getId(), d.getCode(), d.getName(), d.getDescription(), d.getContactEmail(), d.isActive());
        }
    }

    public record CategoryDto(Long id, String name, String description, Long departmentId, String departmentName,
                              int slaHours, Priority defaultPriority, boolean active) {
        public static CategoryDto of(ComplaintCategory c) {
            return new CategoryDto(c.getId(), c.getName(), c.getDescription(), c.getDepartment().getId(),
                    c.getDepartment().getName(), c.getSlaHours(), c.getDefaultPriority(), c.isActive());
        }
    }

    public record DepartmentRequest(
            @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Use letters, digits, - or _") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Email @Size(max = 160) String contactEmail,
            @NotNull Boolean active) {}

    public record CategoryRequest(
            @NotNull Long departmentId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @Min(1) @Max(2160) Integer slaHours,
            @NotNull Priority defaultPriority,
            @NotNull Boolean active) {}
}
