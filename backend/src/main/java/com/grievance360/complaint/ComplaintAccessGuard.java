package com.grievance360.complaint;

import com.grievance360.security.UserPrincipal;
import com.grievance360.user.Role;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Resource-level authorization for complaints. Role checks alone are not sufficient. */
@Component
public class ComplaintAccessGuard {

    public boolean canView(UserPrincipal p, Complaint c) {
        return switch (p.role()) {
            case ADMIN -> true;
            case CITIZEN -> Objects.equals(c.getCitizen().getId(), p.id());
            case OFFICER -> isAssignedTo(c, p) || inDepartment(c, p);
        };
    }

    /** Officers act on complaints in their department that are unassigned or assigned to them; admins on all. */
    public boolean canManage(UserPrincipal p, Complaint c) {
        if (p.is(Role.ADMIN)) return true;
        if (!p.is(Role.OFFICER)) return false;
        if (isAssignedTo(c, p)) return true;
        return inDepartment(c, p) && c.getAssignedOfficer() == null;
    }

    public boolean isOwner(UserPrincipal p, Complaint c) {
        return p.is(Role.CITIZEN) && Objects.equals(c.getCitizen().getId(), p.id());
    }

    public boolean isStaff(UserPrincipal p) {
        return p.is(Role.OFFICER) || p.is(Role.ADMIN);
    }

    private static boolean isAssignedTo(Complaint c, UserPrincipal p) {
        return c.getAssignedOfficer() != null && Objects.equals(c.getAssignedOfficer().getId(), p.id());
    }

    private static boolean inDepartment(Complaint c, UserPrincipal p) {
        return p.departmentId() != null && Objects.equals(c.getDepartment().getId(), p.departmentId());
    }
}
