package com.grievance360.complaint;

import com.grievance360.audit.AuditService;
import com.grievance360.common.ApiException;
import com.grievance360.common.PageResponse;
import com.grievance360.complaint.ComplaintDtos.*;
import com.grievance360.config.AppProperties;
import com.grievance360.department.CategoryRepository;
import com.grievance360.department.ComplaintCategory;
import com.grievance360.notification.Notification;
import com.grievance360.notification.NotificationService;
import com.grievance360.security.UserPrincipal;
import com.grievance360.settings.SettingsService;
import com.grievance360.user.Role;
import com.grievance360.user.User;
import com.grievance360.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.grievance360.complaint.ComplaintStatus.*;

@Service
@RequiredArgsConstructor
public class ComplaintService {
    private final ComplaintRepository complaints;
    private final StatusHistoryRepository history;
    private final CommentRepository comments;
    private final AttachmentRepository attachments;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final ReferenceGenerator references;
    private final ComplaintAccessGuard guard;
    private final NotificationService notifications;
    private final SettingsService settings;
    private final AuditService audit;
    private final AppProperties props;

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<ComplaintSummary> list(ComplaintFilter filter, Pageable pageable, UserPrincipal p) {
        Instant now = Instant.now();
        return PageResponse.of(complaints.findAll(filter.toSpec(p), pageable), c -> ComplaintMapper.summary(c, now));
    }

    @Transactional(readOnly = true)
    public ComplaintDetail get(Long id, UserPrincipal p) {
        return toDetail(loadVisible(id, p), p);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(Long id, UserPrincipal p) {
        loadVisible(id, p);
        return history.findByComplaintIdOrderByCreatedAtAscIdAsc(id).stream().map(ComplaintMapper::history).toList();
    }

    @Transactional(readOnly = true)
    public List<CommentDto> comments(Long id, UserPrincipal p) {
        loadVisible(id, p);
        var list = guard.isStaff(p) ? comments.findByComplaintIdOrderByCreatedAtAscIdAsc(id)
                : comments.findByComplaintIdAndVisibilityOrderByCreatedAtAscIdAsc(id, Comment.Visibility.PUBLIC);
        return list.stream().map(c -> ComplaintMapper.comment(c, p)).toList();
    }

    @Transactional(readOnly = true)
    public PublicStatus publicLookup(String reference) {
        Complaint c = complaints.findByReferenceIgnoreCase(reference.trim())
                .orElseThrow(() -> ApiException.notFound("Complaint"));
        var steps = history.findByComplaintIdOrderByCreatedAtAscIdAsc(c.getId()).stream()
                .filter(h -> h.getFromStatus() != h.getToStatus())
                .map(h -> new PublicStep(h.getToStatus(), h.getCreatedAt())).toList();
        return new PublicStatus(c.getReference(), c.getCategory().getName(), c.getDepartment().getName(), c.getStatus(),
                c.getCreatedAt(), c.getUpdatedAt(), c.getResolvedAt(), steps);
    }

    // ------------------------------------------------------------------ citizen operations

    @Transactional
    public ComplaintDetail create(CreateComplaintRequest req, UserPrincipal p) {
        if (!p.is(Role.CITIZEN)) throw ApiException.forbidden("Only citizens can submit complaints");
        ComplaintCategory cat = categories.findWithDepartmentById(req.categoryId())
                .filter(ComplaintCategory::isActive)
                .filter(x -> x.getDepartment().isActive())
                .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "Select a valid, active category"));

        Instant now = Instant.now();
        Complaint c = new Complaint();
        c.setReference(references.next());
        c.setTitle(req.title().trim());
        c.setDescription(req.description().trim());
        c.setCategory(cat);
        c.setDepartment(cat.getDepartment());
        c.setCitizen(users.getReferenceById(p.id()));
        c.setStatus(SUBMITTED);
        c.setPriority(cat.getDefaultPriority());
        c.setLocationAddress(trim(req.locationAddress()));
        c.setLocationWard(trim(req.locationWard()));
        c.setLocationCity(trim(req.locationCity()));
        c.setLocationPincode(trim(req.locationPincode()));
        c.setLatitude(req.latitude());
        c.setLongitude(req.longitude());
        c.setDueAt(computeDue(now, cat.getSlaHours(), c.getPriority()));
        complaints.save(c);
        writeHistory(c, null, SUBMITTED, "Complaint submitted", p);

        notifications.notifyAll(users.findByRoleAndDepartmentIdAndActiveTrueOrderByFullName(Role.OFFICER, c.getDepartment().getId()),
                c, Notification.Type.NEW_COMPLAINT, "New complaint " + c.getReference(), c.getTitle());
        return toDetail(complaints.findDetailedById(c.getId()).orElseThrow(), p);
    }

    @Transactional
    public CommentDto addComment(Long id, CommentRequest req, UserPrincipal p) {
        Complaint c = loadVisible(id, p);
        Comment.Visibility visibility;
        if (guard.isStaff(p)) {
            if (!guard.canManage(p, c) && !p.is(Role.OFFICER)) throw ApiException.forbidden("Not permitted");
            visibility = req.visibility() == null ? Comment.Visibility.PUBLIC : req.visibility();
        } else {
            if (c.getStatus() == CLOSED || c.getStatus() == REJECTED) {
                throw ApiException.conflict("COMPLAINT_FINALISED", "This complaint is closed; reopen it or submit a new complaint");
            }
            visibility = Comment.Visibility.PUBLIC; // citizens can never post internal notes
        }
        Comment cm = new Comment();
        cm.setComplaint(c);
        cm.setAuthor(users.getReferenceById(p.id()));
        cm.setBody(req.body().trim());
        cm.setVisibility(visibility);
        comments.save(cm);

        if (p.is(Role.CITIZEN)) {
            if (c.getStatus() == NEEDS_INFORMATION) {
                // Citizen supplied the requested information: resume the stage the complaint was paused in.
                ComplaintStatus resume = history.findFirstByComplaintIdAndToStatusOrderByIdDesc(c.getId(), NEEDS_INFORMATION)
                        .map(StatusHistory::getFromStatus).orElse(ACKNOWLEDGED);
                if (!StatusTransitionPolicy.isAllowed(NEEDS_INFORMATION, resume, Role.CITIZEN)) resume = ACKNOWLEDGED;
                transition(c, resume, "Citizen provided the requested information", p);
            }
            User target = c.getAssignedOfficer();
            if (target != null) {
                notifications.notify(target, c, Notification.Type.COMMENT, "Citizen replied on " + c.getReference(), cm.getBody());
            }
        } else if (visibility == Comment.Visibility.PUBLIC) {
            notifications.notify(c.getCitizen(), c, Notification.Type.COMMENT, "New remark on " + c.getReference(), cm.getBody());
        }
        complaints.save(c);
        return ComplaintMapper.comment(comments.findById(cm.getId()).orElse(cm), p);
    }

    @Transactional
    public ComplaintDetail reopen(Long id, ReopenRequest req, UserPrincipal p) {
        Complaint c = loadVisible(id, p);
        if (!guard.isOwner(p, c) && !p.is(Role.ADMIN)) throw ApiException.forbidden("Only the complainant can reopen this complaint");
        if (c.getStatus() != RESOLVED) throw ApiException.conflict("INVALID_TRANSITION", "Only resolved complaints can be reopened");
        int window = props.workflow().reopenWindowDays();
        if (p.is(Role.CITIZEN) && c.getResolvedAt() != null
                && c.getResolvedAt().plus(Duration.ofDays(window)).isBefore(Instant.now())) {
            throw ApiException.conflict("REOPEN_WINDOW_EXPIRED", "Complaints can be reopened within " + window + " days of resolution");
        }
        if (p.is(Role.CITIZEN) && c.getReopenCount() >= 3) {
            throw ApiException.conflict("REOPEN_LIMIT", "This complaint has been reopened the maximum number of times");
        }
        ComplaintStatus target = c.getAssignedOfficer() != null ? IN_PROGRESS : ACKNOWLEDGED;
        if (target == ACKNOWLEDGED) {
            // RESOLVED without an officer is unusual (admin-resolved); resume at acknowledgement.
            writeHistory(c, RESOLVED, ACKNOWLEDGED, "Reopened: " + req.reason().trim(), p);
            c.setStatus(ACKNOWLEDGED);
            c.setResolvedAt(null);
        } else {
            transition(c, IN_PROGRESS, "Reopened: " + req.reason().trim(), p);
        }
        c.setReopenCount(c.getReopenCount() + 1);
        if (c.getAssignedOfficer() != null) {
            notifications.notify(c.getAssignedOfficer(), c, Notification.Type.STATUS_CHANGED,
                    c.getReference() + " was reopened", req.reason().trim());
        }
        return toDetail(complaints.save(c), p);
    }

    @Transactional
    public ComplaintDetail confirmClose(Long id, CloseRequest req, UserPrincipal p) {
        Complaint c = loadVisible(id, p);
        if (!guard.isOwner(p, c)) throw ApiException.forbidden("Only the complainant can confirm resolution");
        if (c.getStatus() != RESOLVED) throw ApiException.conflict("INVALID_TRANSITION", "Only resolved complaints can be closed");
        String remark = req == null || req.feedback() == null || req.feedback().isBlank()
                ? "Citizen confirmed the resolution" : "Citizen confirmed the resolution: " + req.feedback().trim();
        transition(c, CLOSED, remark, p);
        return toDetail(complaints.save(c), p);
    }

    // ------------------------------------------------------------------ staff operations

    @Transactional
    public ComplaintDetail updateStatus(Long id, StatusUpdateRequest req, UserPrincipal p) {
        Complaint c = loadManageable(id, p);
        ComplaintStatus to = req.status();
        Set<ComplaintStatus> allowed = StatusTransitionPolicy.staffFormTargets(c.getStatus(), p.role(), c.getAssignedOfficer() != null);
        if (!allowed.contains(to)) {
            throw ApiException.conflict("INVALID_TRANSITION",
                    "Cannot change status from " + c.getStatus() + " to " + to);
        }
        String remark = trim(req.remark());
        if (StatusTransitionPolicy.REMARK_REQUIRED.contains(to) && remark == null) {
            throw ApiException.badRequest("REMARK_REQUIRED", "A remark is required for this status");
        }
        if (to == RESOLVED) {
            String notes = trim(req.resolutionNotes());
            if (notes == null || notes.length() < 10) {
                throw ApiException.badRequest("RESOLUTION_REQUIRED", "Describe the resolution (at least 10 characters)");
            }
            c.setResolutionNotes(notes);
        }
        if (p.is(Role.OFFICER) && (to == IN_PROGRESS || to == RESOLVED) && !isAssignedTo(c, p)) {
            throw ApiException.forbidden("Only the assigned officer can progress this complaint");
        }
        transition(c, to, remark, p);
        Notification.Type type = to == NEEDS_INFORMATION ? Notification.Type.INFO_REQUESTED : Notification.Type.STATUS_CHANGED;
        String msg = remark != null ? remark : "Status changed to " + label(to);
        notifications.notify(c.getCitizen(), c, type, c.getReference() + ": " + label(to), msg);
        return toDetail(complaints.save(c), p);
    }

    @Transactional
    public ComplaintDetail assign(Long id, AssignRequest req, UserPrincipal p) {
        Complaint c = loadVisible(id, p);
        if (!guard.isStaff(p)) throw ApiException.notFound("Complaint");
        if (!StatusTransitionPolicy.ASSIGNABLE.contains(c.getStatus())) {
            throw ApiException.conflict("NOT_ASSIGNABLE", "Complaints in status " + c.getStatus() + " cannot be assigned");
        }
        if (p.is(Role.OFFICER)) {
            // Officers may only claim unassigned complaints of their own department for themselves.
            if (!Objects.equals(req.officerId(), p.id()) || c.getAssignedOfficer() != null
                    || !Objects.equals(c.getDepartment().getId(), p.departmentId())) {
                throw ApiException.forbidden("Officers can only claim unassigned complaints in their department");
            }
        }
        User officer = users.findWithDepartmentById(req.officerId())
                .filter(u -> u.getRole() == Role.OFFICER && u.isActive())
                .orElseThrow(() -> ApiException.badRequest("INVALID_OFFICER", "Select an active officer"));
        if (officer.getDepartment() == null || !officer.getDepartment().getId().equals(c.getDepartment().getId())) {
            throw ApiException.badRequest("OFFICER_DEPARTMENT_MISMATCH", "Officer does not belong to the complaint's department");
        }
        if (c.getAssignedOfficer() != null && c.getAssignedOfficer().getId().equals(officer.getId())) {
            throw ApiException.conflict("ALREADY_ASSIGNED", "Complaint is already assigned to this officer");
        }
        User previous = c.getAssignedOfficer();
        c.setAssignedOfficer(officer);
        c.setAssignedAt(Instant.now());
        String remark = (previous == null ? "Assigned to " : "Reassigned from " + previous.getFullName() + " to ")
                + officer.getFullName() + (trim(req.remark()) == null ? "" : ". " + req.remark().trim());
        if (c.getStatus() == SUBMITTED || c.getStatus() == ACKNOWLEDGED) {
            transition(c, ASSIGNED, remark, p);
        } else {
            writeHistory(c, c.getStatus(), c.getStatus(), remark, p); // assignment change recorded without status change
        }
        notifications.notify(officer, c, Notification.Type.ASSIGNED, "Assigned: " + c.getReference(), c.getTitle());
        if (previous != null) {
            notifications.notify(previous, c, Notification.Type.ASSIGNED, "Reassigned: " + c.getReference(),
                    "This complaint was reassigned to " + officer.getFullName());
        }
        notifications.notify(c.getCitizen(), c, Notification.Type.ASSIGNED, c.getReference() + " assigned",
                "Your complaint has been assigned to an officer in " + c.getDepartment().getName());
        audit.record(p, previous == null ? "COMPLAINT_ASSIGNED" : "COMPLAINT_REASSIGNED", "COMPLAINT", c.getId(),
                c.getReference() + " -> officer " + officer.getId());
        return toDetail(complaints.save(c), p);
    }

    @Transactional
    public ComplaintDetail changePriority(Long id, PriorityRequest req, UserPrincipal p) {
        Complaint c = loadManageable(id, p);
        if (!c.getStatus().isOpen()) throw ApiException.conflict("COMPLAINT_FINALISED", "Priority can only change while the complaint is open");
        if (c.getPriority() == req.priority()) return toDetail(c, p);
        Priority old = c.getPriority();
        c.setPriority(req.priority());
        c.setDueAt(computeDue(c.getCreatedAt(), c.getCategory().getSlaHours(), req.priority()));
        String reason = trim(req.reason());
        writeHistory(c, c.getStatus(), c.getStatus(), "Priority changed from " + old + " to " + req.priority()
                + (reason == null ? "" : ". " + reason), p);
        audit.record(p, "COMPLAINT_PRIORITY_CHANGED", "COMPLAINT", c.getId(), old + " -> " + req.priority());
        return toDetail(complaints.save(c), p);
    }

    @Transactional
    public ComplaintDetail reroute(Long id, RerouteRequest req, UserPrincipal p) {
        if (!p.is(Role.ADMIN)) throw ApiException.forbidden("Only administrators can reroute complaints");
        Complaint c = loadVisible(id, p);
        if (!c.getStatus().isOpen()) throw ApiException.conflict("COMPLAINT_FINALISED", "Only open complaints can be rerouted");
        ComplaintCategory cat = categories.findWithDepartmentById(req.categoryId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_CATEGORY", "Select a valid category"));
        boolean deptChanged = !cat.getDepartment().getId().equals(c.getDepartment().getId());
        c.setCategory(cat);
        c.setDepartment(cat.getDepartment());
        c.setDueAt(computeDue(c.getCreatedAt(), cat.getSlaHours(), c.getPriority()));
        String remark = "Rerouted to " + cat.getDepartment().getName() + " / " + cat.getName() + ". " + req.remark().trim();
        if (deptChanged && c.getAssignedOfficer() != null) {
            c.setAssignedOfficer(null);
            c.setAssignedAt(null);
            if (c.getStatus() == ASSIGNED || c.getStatus() == IN_PROGRESS) {
                writeHistory(c, c.getStatus(), ACKNOWLEDGED, remark, p);
                c.setStatus(ACKNOWLEDGED);
            } else {
                writeHistory(c, c.getStatus(), c.getStatus(), remark, p);
            }
        } else {
            writeHistory(c, c.getStatus(), c.getStatus(), remark, p);
        }
        audit.record(p, "COMPLAINT_REROUTED", "COMPLAINT", c.getId(), "category " + cat.getId());
        return toDetail(complaints.save(c), p);
    }

    // ------------------------------------------------------------------ system operations (scheduler)

    @Transactional
    public void systemClose(Complaint c) {
        writeHistory(c, c.getStatus(), CLOSED, "Closed automatically " + props.workflow().autoCloseDays()
                + " days after resolution", null);
        c.setStatus(CLOSED);
        c.setClosedAt(Instant.now());
        complaints.save(c);
    }

    // ------------------------------------------------------------------ internals

    public Instant computeDue(Instant from, int slaHours, Priority priority) {
        int percent = switch (priority) {
            case CRITICAL -> settings.getInt(SettingsService.SLA_CRITICAL_PERCENT, 50);
            case HIGH -> settings.getInt(SettingsService.SLA_HIGH_PERCENT, 75);
            default -> 100;
        };
        long minutes = Math.max(60, (long) slaHours * 60 * percent / 100);
        return from.plus(Duration.ofMinutes(minutes));
    }

    private void transition(Complaint c, ComplaintStatus to, String remark, UserPrincipal p) {
        ComplaintStatus from = c.getStatus();
        if (!StatusTransitionPolicy.isAllowed(from, to, p.role()) && !(to == ASSIGNED && EnumSet.of(SUBMITTED, ACKNOWLEDGED).contains(from))) {
            throw ApiException.conflict("INVALID_TRANSITION", "Cannot change status from " + from + " to " + to);
        }
        Instant now = Instant.now();
        switch (to) {
            case RESOLVED -> c.setResolvedAt(now);
            case CLOSED -> c.setClosedAt(now);
            case IN_PROGRESS -> {
                if (from == RESOLVED) {
                    c.setResolvedAt(null);
                }
            }
            default -> { }
        }
        c.setStatus(to);
        writeHistory(c, from, to, remark, p);
    }

    private void writeHistory(Complaint c, ComplaintStatus from, ComplaintStatus to, String remark, UserPrincipal p) {
        StatusHistory h = new StatusHistory();
        h.setComplaint(c);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setRemark(remark == null ? null : remark.length() > 2000 ? remark.substring(0, 2000) : remark);
        h.setActor(p == null ? null : users.getReferenceById(p.id()));
        h.setActorRole(p == null ? "SYSTEM" : p.role().name());
        history.save(h);
    }

    Complaint loadVisible(Long id, UserPrincipal p) {
        Complaint c = complaints.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Complaint"));
        // Return 404 rather than 403 so callers cannot probe for other citizens' complaint IDs.
        if (!guard.canView(p, c)) throw ApiException.notFound("Complaint");
        return c;
    }

    private Complaint loadManageable(Long id, UserPrincipal p) {
        Complaint c = loadVisible(id, p);
        if (!guard.isStaff(p)) throw ApiException.forbidden("Only officers and administrators can perform this action");
        if (!guard.canManage(p, c)) throw ApiException.forbidden("This complaint is assigned to another officer");
        return c;
    }

    private ComplaintDetail toDetail(Complaint c, UserPrincipal p) {
        boolean staff = guard.isStaff(p);
        boolean manage = staff && guard.canManage(p, c);
        boolean owner = guard.isOwner(p, c);
        boolean open = c.getStatus().isOpen();
        boolean withinReopen = c.getResolvedAt() != null
                && c.getResolvedAt().plus(Duration.ofDays(props.workflow().reopenWindowDays())).isAfter(Instant.now());

        Set<ComplaintStatus> targets = manage
                ? StatusTransitionPolicy.staffFormTargets(c.getStatus(), p.role(), c.getAssignedOfficer() != null)
                : EnumSet.noneOf(ComplaintStatus.class);
        if (p.is(Role.OFFICER) && !isAssignedTo(c, p)) {
            targets.remove(IN_PROGRESS);
            targets.remove(RESOLVED);
        }
        boolean assignable = StatusTransitionPolicy.ASSIGNABLE.contains(c.getStatus());
        var actions = new AllowedActions(targets,
                p.is(Role.ADMIN) && assignable,
                p.is(Role.OFFICER) && assignable && c.getAssignedOfficer() == null
                        && Objects.equals(c.getDepartment().getId(), p.departmentId()),
                manage && open,
                p.is(Role.ADMIN) && open,
                (owner && c.getStatus() != CLOSED && c.getStatus() != REJECTED) || staff,
                staff,
                (owner || manage) && c.getStatus() != CLOSED && c.getStatus() != REJECTED,
                (owner && c.getStatus() == RESOLVED && withinReopen && c.getReopenCount() < 3) || (p.is(Role.ADMIN) && c.getStatus() == RESOLVED),
                owner && c.getStatus() == RESOLVED);
        var atts = attachments.findByComplaintIdOrderByCreatedAtAsc(c.getId()).stream().map(ComplaintMapper::attachment).toList();
        return ComplaintMapper.detail(c, staff, atts, actions);
    }

    private static boolean isAssignedTo(Complaint c, UserPrincipal p) {
        return c.getAssignedOfficer() != null && Objects.equals(c.getAssignedOfficer().getId(), p.id());
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String label(ComplaintStatus s) {
        String l = s.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }
}
