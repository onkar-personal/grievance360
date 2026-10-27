package com.grievance360.audit;

import com.grievance360.common.RequestContext;
import com.grievance360.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repo;

    /** Records an audit event. Never pass passwords, tokens or full personal records in {@code details}. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UserPrincipal actor, String action, String entityType, Object entityId, String details) {
        AuditLog log = new AuditLog();
        if (actor != null) {
            log.setActorId(actor.id());
            log.setActorEmail(actor.email());
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : String.valueOf(entityId));
        log.setDetails(details == null ? null : truncate(details, 1000));
        log.setIpAddress(RequestContext.clientIp());
        repo.save(log);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
