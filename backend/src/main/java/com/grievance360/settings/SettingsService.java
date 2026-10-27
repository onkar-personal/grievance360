package com.grievance360.settings;

import com.grievance360.audit.AuditService;
import com.grievance360.common.ApiException;
import com.grievance360.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {
    public static final String ESCALATION_ENABLED = "escalation.enabled";
    public static final String ESCALATION_REPEAT_HOURS = "escalation.repeat_hours";
    public static final String ESCALATION_MAX_LEVEL = "escalation.max_level";
    public static final String SLA_CRITICAL_PERCENT = "sla.critical_factor_percent";
    public static final String SLA_HIGH_PERCENT = "sla.high_factor_percent";

    /** Allowed keys with their inclusive numeric bounds (booleans use 0..1 as marker). */
    private static final Map<String, int[]> BOUNDS = Map.of(
            ESCALATION_REPEAT_HOURS, new int[]{1, 720},
            ESCALATION_MAX_LEVEL, new int[]{1, 10},
            SLA_CRITICAL_PERCENT, new int[]{10, 100},
            SLA_HIGH_PERCENT, new int[]{10, 100});

    private final SystemSettingRepository repo;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<SystemSetting> all() {
        return repo.findAllByOrderByKeyAsc();
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int fallback) {
        return repo.findById(key).map(s -> {
            try {
                return Integer.parseInt(s.getValue());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }).orElse(fallback);
    }

    @Transactional(readOnly = true)
    public boolean getBool(String key, boolean fallback) {
        return repo.findById(key).map(s -> Boolean.parseBoolean(s.getValue())).orElse(fallback);
    }

    @Transactional
    public SystemSetting update(String key, String value, UserPrincipal actor) {
        SystemSetting s = repo.findById(key).orElseThrow(() -> ApiException.notFound("Setting"));
        String v = value == null ? "" : value.trim();
        if (ESCALATION_ENABLED.equals(key)) {
            if (!v.equals("true") && !v.equals("false")) throw ApiException.badRequest("INVALID_SETTING", "Value must be true or false");
        } else {
            int[] b = BOUNDS.get(key);
            if (b == null) throw ApiException.badRequest("INVALID_SETTING", "Setting is not editable");
            try {
                int n = Integer.parseInt(v);
                if (n < b[0] || n > b[1]) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("INVALID_SETTING", "Value must be a whole number between " + b[0] + " and " + b[1]);
            }
        }
        String old = s.getValue();
        s.setValue(v);
        audit.record(actor, "SETTING_UPDATED", "SETTING", key, old + " -> " + v);
        return s;
    }
}
