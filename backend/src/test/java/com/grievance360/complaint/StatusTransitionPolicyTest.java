package com.grievance360.complaint;

import com.grievance360.user.Role;
import org.junit.jupiter.api.Test;

import static com.grievance360.complaint.ComplaintStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class StatusTransitionPolicyTest {

    @Test
    void officerFollowsForwardWorkflow() {
        assertThat(StatusTransitionPolicy.isAllowed(SUBMITTED, ACKNOWLEDGED, Role.OFFICER)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(ASSIGNED, IN_PROGRESS, Role.OFFICER)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(IN_PROGRESS, RESOLVED, Role.OFFICER)).isTrue();
    }

    @Test
    void skippingStagesIsRejected() {
        assertThat(StatusTransitionPolicy.isAllowed(SUBMITTED, RESOLVED, Role.OFFICER)).isFalse();
        assertThat(StatusTransitionPolicy.isAllowed(SUBMITTED, IN_PROGRESS, Role.ADMIN)).isFalse();
        assertThat(StatusTransitionPolicy.isAllowed(ASSIGNED, CLOSED, Role.ADMIN)).isFalse();
    }

    @Test
    void terminalStatusesHaveNoExits() {
        for (Role r : Role.values()) {
            assertThat(StatusTransitionPolicy.allowedTargets(CLOSED, r)).isEmpty();
            assertThat(StatusTransitionPolicy.allowedTargets(REJECTED, r)).isEmpty();
        }
    }

    @Test
    void officersCannotCloseButAdminsAndCitizensCan() {
        assertThat(StatusTransitionPolicy.isAllowed(RESOLVED, CLOSED, Role.OFFICER)).isFalse();
        assertThat(StatusTransitionPolicy.isAllowed(RESOLVED, CLOSED, Role.ADMIN)).isTrue();
        assertThat(StatusTransitionPolicy.isAllowed(RESOLVED, CLOSED, Role.CITIZEN)).isTrue();
    }

    @Test
    void citizensCannotDriveStaffTransitions() {
        assertThat(StatusTransitionPolicy.isAllowed(SUBMITTED, ACKNOWLEDGED, Role.CITIZEN)).isFalse();
        assertThat(StatusTransitionPolicy.isAllowed(IN_PROGRESS, RESOLVED, Role.CITIZEN)).isFalse();
    }

    @Test
    void resumeFromNeedsInformationDependsOnAssignment() {
        assertThat(StatusTransitionPolicy.staffFormTargets(NEEDS_INFORMATION, Role.OFFICER, false))
                .containsExactlyInAnyOrder(ACKNOWLEDGED, REJECTED);
        assertThat(StatusTransitionPolicy.staffFormTargets(NEEDS_INFORMATION, Role.OFFICER, true))
                .containsExactlyInAnyOrder(ASSIGNED, IN_PROGRESS, REJECTED);
    }
}
