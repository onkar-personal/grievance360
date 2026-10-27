package com.grievance360.complaint;

import com.grievance360.security.UserPrincipal;
import com.grievance360.user.Role;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Server-side filters for complaint listings, always combined with the caller's visibility scope. */
public record ComplaintFilter(String q, List<ComplaintStatus> status, Priority priority, Long departmentId,
                              Long categoryId, Long officerId, Boolean unassigned, Boolean mine, Boolean overdue,
                              Boolean escalated, LocalDate from, LocalDate to) {

    public Specification<Complaint> toSpec(UserPrincipal p) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            // Visibility scope
            if (p.is(Role.CITIZEN)) {
                ps.add(cb.equal(root.get("citizen").get("id"), p.id()));
            } else if (p.is(Role.OFFICER)) {
                var officer = root.join("assignedOfficer", JoinType.LEFT);
                ps.add(p.departmentId() == null
                        ? cb.equal(officer.get("id"), p.id())
                        : cb.or(cb.equal(root.get("department").get("id"), p.departmentId()), cb.equal(officer.get("id"), p.id())));
            }

            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("reference")), like, '\\'),
                        cb.like(cb.lower(root.get("title")), like, '\\')));
            }
            if (status != null && !status.isEmpty()) ps.add(root.get("status").in(status));
            if (priority != null) ps.add(cb.equal(root.get("priority"), priority));
            if (departmentId != null) ps.add(cb.equal(root.get("department").get("id"), departmentId));
            if (categoryId != null) ps.add(cb.equal(root.get("category").get("id"), categoryId));
            if (officerId != null) ps.add(cb.equal(root.get("assignedOfficer").get("id"), officerId));
            if (Boolean.TRUE.equals(unassigned)) {
                ps.add(cb.isNull(root.get("assignedOfficer")));
                ps.add(root.get("status").in(ComplaintStatus.OPEN));
            }
            if (Boolean.TRUE.equals(mine) && !p.is(Role.CITIZEN)) ps.add(cb.equal(root.get("assignedOfficer").get("id"), p.id()));
            if (Boolean.TRUE.equals(overdue)) {
                ps.add(root.get("status").in(ComplaintStatus.OPEN));
                ps.add(cb.lessThan(root.get("dueAt"), Instant.now()));
            }
            if (Boolean.TRUE.equals(escalated)) ps.add(cb.greaterThan(root.get("escalationLevel"), 0));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            if (to != null) ps.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));

            return cb.and(ps.toArray(Predicate[]::new));
        };
    }
}
