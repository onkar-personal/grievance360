package com.grievance360.notification;

import com.grievance360.common.ApiException;
import com.grievance360.common.PageResponse;
import com.grievance360.common.Paging;
import com.grievance360.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {
    private final NotificationRepository repo;

    public record NotificationDto(Long id, String type, String title, String message, Long complaintId,
                                  String complaintReference, Instant readAt, Instant createdAt) {
        static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getType().name(), n.getTitle(), n.getMessage(),
                    n.getComplaint() == null ? null : n.getComplaint().getId(),
                    n.getComplaint() == null ? null : n.getComplaint().getReference(), n.getReadAt(), n.getCreatedAt());
        }
    }

    @GetMapping
    @Operation(summary = "List the current user's notifications")
    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        Long uid = CurrentUser.get().id();
        var pageable = Paging.of(page, size, null, Set.of("createdAt"), "createdAt,desc");
        var result = unreadOnly ? repo.findByUserIdAndReadAtIsNull(uid, pageable) : repo.findByUserId(uid, pageable);
        return PageResponse.of(result, NotificationDto::of);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications")
    public Map<String, Long> unreadCount() {
        return Map.of("count", repo.countByUserIdAndReadAtIsNull(CurrentUser.get().id()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    @Transactional
    public void markRead(@PathVariable Long id) {
        Notification n = repo.findByIdAndUserId(id, CurrentUser.get().id()).orElseThrow(() -> ApiException.notFound("Notification"));
        if (n.getReadAt() == null) n.setReadAt(Instant.now());
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    @Transactional
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", repo.markAllRead(CurrentUser.get().id(), Instant.now()));
    }
}
