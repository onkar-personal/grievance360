package com.grievance360.escalation;

import com.grievance360.complaint.Complaint;
import com.grievance360.complaint.ComplaintRepository;
import com.grievance360.complaint.ComplaintService;
import com.grievance360.complaint.ComplaintStatus;
import com.grievance360.config.AppProperties;
import com.grievance360.notification.Notification;
import com.grievance360.notification.NotificationService;
import com.grievance360.settings.SettingsService;
import com.grievance360.user.Role;
import com.grievance360.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Raises the escalation level of overdue complaints and auto-closes long-resolved ones. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscalationJob {
    private final ComplaintRepository complaints;
    private final ComplaintService complaintService;
    private final NotificationService notifications;
    private final UserRepository users;
    private final SettingsService settings;
    private final AppProperties props;

    @Scheduled(cron = "${app.workflow.escalation-cron}")
    @Transactional
    public void run() {
        int escalated = escalate(Instant.now());
        int closed = autoClose(Instant.now());
        if (escalated + closed > 0) log.info("Escalation job: escalated={}, autoClosed={}", escalated, closed);
    }

    @Transactional
    public int escalate(Instant now) {
        if (!settings.getBool(SettingsService.ESCALATION_ENABLED, true)) return 0;
        int repeat = settings.getInt(SettingsService.ESCALATION_REPEAT_HOURS, 24);
        int max = settings.getInt(SettingsService.ESCALATION_MAX_LEVEL, 3);
        var candidates = complaints.findEscalationCandidates(ComplaintStatus.OPEN, now, max, now.minus(Duration.ofHours(repeat)));
        var admins = users.findByRoleAndActiveTrue(Role.ADMIN);
        for (Complaint c : candidates) {
            c.setEscalationLevel(c.getEscalationLevel() + 1);
            c.setEscalatedAt(now);
            String msg = c.getReference() + " is past its SLA due time (level " + c.getEscalationLevel() + ")";
            notifications.notifyAll(admins, c, Notification.Type.ESCALATED, "Escalated: " + c.getReference(), msg);
            if (c.getAssignedOfficer() != null) {
                notifications.notify(c.getAssignedOfficer(), c, Notification.Type.ESCALATED, "Escalated: " + c.getReference(), msg);
            }
        }
        return candidates.size();
    }

    @Transactional
    public int autoClose(Instant now) {
        var list = complaints.findResolvedBefore(now.minus(Duration.ofDays(props.workflow().autoCloseDays())));
        list.forEach(complaintService::systemClose);
        return list.size();
    }
}
