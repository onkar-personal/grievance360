package com.grievance360.complaint;

import java.util.EnumSet;
import java.util.Set;

public enum ComplaintStatus {
    SUBMITTED, ACKNOWLEDGED, ASSIGNED, IN_PROGRESS, NEEDS_INFORMATION, RESOLVED, CLOSED, REJECTED;

    /** Statuses in which the complaint still awaits action and counts toward SLA. */
    public static final Set<ComplaintStatus> OPEN = EnumSet.of(SUBMITTED, ACKNOWLEDGED, ASSIGNED, IN_PROGRESS, NEEDS_INFORMATION);

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
