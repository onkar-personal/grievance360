package com.grievance360.complaint;

import com.grievance360.user.Role;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.grievance360.complaint.ComplaintStatus.*;

/**
 * Single source of truth for which status changes each role may perform through the generic status endpoint.
 * ASSIGNED is entered only through assignment; reopening and citizen replies use their own operations but are
 * validated here as well.
 */
public final class StatusTransitionPolicy {
    private static final Map<ComplaintStatus, Set<ComplaintStatus>> STAFF = new EnumMap<>(ComplaintStatus.class);
    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ADMIN_EXTRA = new EnumMap<>(ComplaintStatus.class);
    private static final Map<ComplaintStatus, Set<ComplaintStatus>> CITIZEN = new EnumMap<>(ComplaintStatus.class);

    /** Statuses from which a complaint can be (re)assigned to an officer. */
    public static final Set<ComplaintStatus> ASSIGNABLE = EnumSet.of(SUBMITTED, ACKNOWLEDGED, ASSIGNED, IN_PROGRESS, NEEDS_INFORMATION);

    /** Statuses that require a remark explaining the change. */
    public static final Set<ComplaintStatus> REMARK_REQUIRED = EnumSet.of(REJECTED, NEEDS_INFORMATION);

    static {
        STAFF.put(SUBMITTED, EnumSet.of(ACKNOWLEDGED, REJECTED));
        STAFF.put(ACKNOWLEDGED, EnumSet.of(NEEDS_INFORMATION, REJECTED));
        STAFF.put(ASSIGNED, EnumSet.of(IN_PROGRESS, NEEDS_INFORMATION, REJECTED));
        STAFF.put(IN_PROGRESS, EnumSet.of(RESOLVED, NEEDS_INFORMATION));
        STAFF.put(NEEDS_INFORMATION, EnumSet.of(ACKNOWLEDGED, ASSIGNED, IN_PROGRESS, REJECTED));
        STAFF.put(RESOLVED, EnumSet.noneOf(ComplaintStatus.class));
        STAFF.put(CLOSED, EnumSet.noneOf(ComplaintStatus.class));
        STAFF.put(REJECTED, EnumSet.noneOf(ComplaintStatus.class));

        ADMIN_EXTRA.put(RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS));

        CITIZEN.put(RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS));
        CITIZEN.put(NEEDS_INFORMATION, EnumSet.of(ACKNOWLEDGED, ASSIGNED, IN_PROGRESS, SUBMITTED));
    }

    private StatusTransitionPolicy() {}

    public static boolean isAllowed(ComplaintStatus from, ComplaintStatus to, Role role) {
        return allowedTargets(from, role).contains(to);
    }

    public static Set<ComplaintStatus> allowedTargets(ComplaintStatus from, Role role) {
        EnumSet<ComplaintStatus> result = EnumSet.noneOf(ComplaintStatus.class);
        switch (role) {
            case CITIZEN -> result.addAll(CITIZEN.getOrDefault(from, Set.of()));
            case OFFICER -> result.addAll(STAFF.getOrDefault(from, Set.of()));
            case ADMIN -> {
                result.addAll(STAFF.getOrDefault(from, Set.of()));
                result.addAll(ADMIN_EXTRA.getOrDefault(from, Set.of()));
            }
        }
        return result;
    }

    /** Targets a staff member may choose in the generic status form (excludes ASSIGNED unless resuming). */
    public static Set<ComplaintStatus> staffFormTargets(ComplaintStatus from, Role role, boolean hasOfficer) {
        Set<ComplaintStatus> t = allowedTargets(from, role);
        if (from == NEEDS_INFORMATION) {
            // Resume to the stage that matches assignment state.
            t.remove(hasOfficer ? ACKNOWLEDGED : ASSIGNED);
            if (!hasOfficer) t.remove(IN_PROGRESS);
        }
        return t;
    }
}
