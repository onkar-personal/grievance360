package com.grievance360.settings;

import com.grievance360.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin: settings")
public class SettingsController {
    private final SettingsService service;

    public record SettingDto(String key, String value, String description, Instant updatedAt) {
        static SettingDto of(SystemSetting s) {
            return new SettingDto(s.getKey(), s.getValue(), s.getDescription(), s.getUpdatedAt());
        }
    }

    public record UpdateSettingRequest(@NotBlank @Size(max = 200) String value) {}

    @GetMapping
    @Operation(summary = "List escalation and SLA settings")
    public List<SettingDto> list() {
        return service.all().stream().map(SettingDto::of).toList();
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update a setting")
    public SettingDto update(@PathVariable String key, @Valid @RequestBody UpdateSettingRequest req) {
        return SettingDto.of(service.update(key, req.value(), CurrentUser.get()));
    }
}
