package com.grievance360.complaint;

import com.grievance360.complaint.ComplaintDtos.*;
import com.grievance360.security.UserPrincipal;
import com.grievance360.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

final class ComplaintMapper {
    private ComplaintMapper() {}

    static PersonRef person(User u) {
        return u == null ? null : new PersonRef(u.getId(), u.getFullName());
    }

    static ComplaintSummary summary(Complaint c, Instant now) {
        return new ComplaintSummary(c.getId(), c.getReference(), c.getTitle(), c.getStatus(), c.getPriority(),
                c.getCategory().getId(), c.getCategory().getName(), c.getDepartment().getId(), c.getDepartment().getName(),
                person(c.getCitizen()), person(c.getAssignedOfficer()), c.getLocationCity(), c.getCreatedAt(),
                c.getUpdatedAt(), c.getDueAt(), c.isOverdue(now), c.getEscalationLevel());
    }

    static ComplaintDetail detail(Complaint c, boolean staffView, List<AttachmentDto> attachments, AllowedActions actions) {
        return new ComplaintDetail(c.getId(), c.getReference(), c.getTitle(), c.getDescription(), c.getStatus(),
                c.getPriority(), c.getCategory().getId(), c.getCategory().getName(), c.getDepartment().getId(),
                c.getDepartment().getName(), person(c.getCitizen()),
                staffView ? c.getCitizen().getEmail() : null, staffView ? c.getCitizen().getPhone() : null,
                person(c.getAssignedOfficer()), c.getLocationAddress(), c.getLocationWard(), c.getLocationCity(),
                c.getLocationPincode(), c.getLatitude(), c.getLongitude(), c.getResolutionNotes(), c.getReopenCount(),
                c.getEscalationLevel(), c.getEscalatedAt(), c.getDueAt(), c.isOverdue(Instant.now()), c.getAssignedAt(),
                c.getResolvedAt(), c.getClosedAt(), c.getCreatedAt(), c.getUpdatedAt(), attachments, actions);
    }

    static HistoryEntry history(StatusHistory h) {
        return new HistoryEntry(h.getId(), h.getFromStatus(), h.getToStatus(), h.getRemark(),
                h.getActor() == null ? "System" : h.getActor().getFullName(), h.getActorRole(), h.getCreatedAt());
    }

    static CommentDto comment(Comment c, UserPrincipal p) {
        return new CommentDto(c.getId(), c.getBody(), c.getVisibility(), c.getAuthor().getFullName(),
                c.getAuthor().getRole().name(), Objects.equals(c.getAuthor().getId(), p.id()), c.getCreatedAt());
    }

    static AttachmentDto attachment(Attachment a) {
        return new AttachmentDto(a.getId(), a.getOriginalName(), a.getContentType(), a.getSizeBytes(),
                a.getUploadedBy().getFullName(), a.getCreatedAt());
    }
}
