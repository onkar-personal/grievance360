package com.grievance360.complaint;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class ComplaintDtos {
    private ComplaintDtos() {}

    // ---------- requests ----------

    /** Citizens choose a category; department, priority, status, reference and SLA are derived on the server. */
    public record CreateComplaintRequest(
            @NotBlank @Size(min = 5, max = 160) String title,
            @NotBlank @Size(min = 20, max = 5000) String description,
            @NotNull Long categoryId,
            @Size(max = 300) String locationAddress,
            @Size(max = 80) String locationWard,
            @Size(max = 80) String locationCity,
            @Pattern(regexp = "^$|^[0-9]{6}$", message = "PIN code must be 6 digits") String locationPincode,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}

    public record StatusUpdateRequest(
            @NotNull ComplaintStatus status,
            @Size(max = 2000) String remark,
            @Size(max = 3000) String resolutionNotes) {}

    public record AssignRequest(@NotNull Long officerId, @Size(max = 2000) String remark) {}

    public record PriorityRequest(@NotNull Priority priority, @Size(max = 500) String reason) {}

    public record RerouteRequest(@NotNull Long categoryId, @NotBlank @Size(max = 2000) String remark) {}

    public record ReopenRequest(@NotBlank @Size(min = 10, max = 2000) String reason) {}

    public record CloseRequest(@Size(max = 2000) String feedback) {}

    public record CommentRequest(@NotBlank @Size(max = 3000) String body, Comment.Visibility visibility) {}

    // ---------- responses ----------

    public record PersonRef(Long id, String name) {}

    public record ComplaintSummary(Long id, String reference, String title, ComplaintStatus status, Priority priority,
                                   Long categoryId, String categoryName, Long departmentId, String departmentName,
                                   PersonRef citizen, PersonRef assignedOfficer, String locationCity,
                                   Instant createdAt, Instant updatedAt, Instant dueAt, boolean overdue,
                                   int escalationLevel) {}

    public record ComplaintDetail(Long id, String reference, String title, String description, ComplaintStatus status,
                                  Priority priority, Long categoryId, String categoryName, Long departmentId,
                                  String departmentName, PersonRef citizen, String citizenEmail, String citizenPhone,
                                  PersonRef assignedOfficer, String locationAddress, String locationWard,
                                  String locationCity, String locationPincode, BigDecimal latitude, BigDecimal longitude,
                                  String resolutionNotes, int reopenCount, int escalationLevel, Instant escalatedAt,
                                  Instant dueAt, boolean overdue, Instant assignedAt, Instant resolvedAt, Instant closedAt,
                                  Instant createdAt, Instant updatedAt, List<AttachmentDto> attachments,
                                  AllowedActions actions) {}

    /** What the current caller may do; the UI uses it to show controls, the server re-checks every action. */
    public record AllowedActions(Set<ComplaintStatus> statusTargets, boolean canAssign, boolean canClaim,
                                 boolean canChangePriority, boolean canReroute, boolean canComment,
                                 boolean canPostInternal, boolean canUpload, boolean canReopen, boolean canConfirmClose) {}

    public record HistoryEntry(Long id, ComplaintStatus fromStatus, ComplaintStatus toStatus, String remark,
                               String actorName, String actorRole, Instant createdAt) {}

    public record CommentDto(Long id, String body, Comment.Visibility visibility, String authorName, String authorRole,
                             boolean mine, Instant createdAt) {}

    public record AttachmentDto(Long id, String originalName, String contentType, long sizeBytes, String uploadedBy,
                                Instant createdAt) {}

    public record PublicStatus(String reference, String categoryName, String departmentName, ComplaintStatus status,
                               Instant submittedAt, Instant lastUpdatedAt, Instant resolvedAt,
                               List<PublicStep> timeline) {}

    public record PublicStep(ComplaintStatus status, Instant at) {}
}
