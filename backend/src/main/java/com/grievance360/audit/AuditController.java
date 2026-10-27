package com.grievance360.audit;

import com.grievance360.common.PageResponse;
import com.grievance360.common.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin: audit")
public class AuditController {
    private final AuditLogRepository repo;

    public record AuditLogDto(Long id, Long actorId, String actorEmail, String action, String entityType, String entityId,
                              String details, String ipAddress, Instant createdAt) {}

    @GetMapping
    @Operation(summary = "Search the audit log")
    public PageResponse<AuditLogDto> list(@RequestParam(required = false) String action,
                                          @RequestParam(required = false) String entityType,
                                          @RequestParam(required = false) String actor,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        Specification<AuditLog> spec = (r, q, cb) -> cb.conjunction();
        if (action != null && !action.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("action"), action));
        if (entityType != null && !entityType.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("entityType"), entityType));
        if (actor != null && !actor.isBlank()) {
            String like = "%" + actor.toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("actorEmail")), like));
        }
        var pageable = Paging.of(page, size, null, Set.of("createdAt"), "createdAt,desc");
        return PageResponse.of(repo.findAll(spec, pageable), a -> new AuditLogDto(a.getId(), a.getActorId(), a.getActorEmail(),
                a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt()));
    }
}
