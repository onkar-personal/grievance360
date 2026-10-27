package com.grievance360.dashboard;

import com.grievance360.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * All metrics are computed from live data. Definitions (also in README):
 * <ul>
 *   <li>open = SUBMITTED, ACKNOWLEDGED, ASSIGNED, IN_PROGRESS, NEEDS_INFORMATION</li>
 *   <li>overdue = open and now &gt; due_at</li>
 *   <li>resolved = resolved_at IS NOT NULL (RESOLVED or CLOSED after resolution)</li>
 *   <li>resolution hours = resolved_at - created_at</li>
 *   <li>SLA compliance % = resolved with resolved_at &lt;= due_at / resolved; null when nothing resolved</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {
    private static final String OPEN = "('SUBMITTED','ACKNOWLEDGED','ASSIGNED','IN_PROGRESS','NEEDS_INFORMATION')";

    private final NamedParameterJdbcTemplate jdbc;

    public record Totals(long total, long open, long overdue, long resolved, long closed, long rejected,
                         long needsInformation, long unassigned, long escalated) {}

    public record Sla(long resolvedCount, long resolvedWithinSla, Double compliancePercent, Double avgResolutionHours,
                      Double medianResolutionHours) {}

    public record Count(String key, long count) {}

    public record TrendPoint(LocalDate date, long submitted, long resolved) {}

    public record DepartmentRow(Long departmentId, String department, long total, long open, long overdue,
                                long resolved, Double compliancePercent, Double avgResolutionHours) {}

    public record OfficerRow(Long officerId, String officer, String department, long open, long overdue,
                             long resolvedLast30, Double avgResolutionHours) {}

    public record CategoryRow(Long categoryId, String category, String department, int slaHours, long total,
                              long resolved, long resolvedWithinSla, Double compliancePercent,
                              Double avgResolutionHours, long overdueOpen) {}

    public record Overview(Totals totals, Sla sla, List<Count> byStatus, List<Count> byPriority,
                           List<TrendPoint> trend, List<DepartmentRow> departments, List<OfficerRow> officers) {}

    public record OfficerDashboard(Totals mine, Sla mySla, List<Count> byStatus, long departmentUnassigned,
                                   long departmentOverdue, List<TrendPoint> trend) {}

    public record CitizenDashboard(Totals totals, List<Count> byStatus) {}

    // ------------------------------------------------------------------

    public Overview adminOverview(int days) {
        String scope = "TRUE";
        var params = new MapSqlParameterSource();
        return new Overview(totals(scope, params), sla(scope, params), byStatus(scope, params), byPriority(scope, params),
                trend(scope, params, days), departments(), officers());
    }

    public OfficerDashboard officer(UserPrincipal p) {
        var params = new MapSqlParameterSource("uid", p.id()).addValue("dept", p.departmentId());
        String mine = "c.assigned_officer_id = :uid";
        long deptUnassigned = p.departmentId() == null ? 0 : count(
                "SELECT count(*) FROM complaints c WHERE c.department_id = :dept AND c.assigned_officer_id IS NULL AND c.status IN " + OPEN, params);
        long deptOverdue = p.departmentId() == null ? 0 : count(
                "SELECT count(*) FROM complaints c WHERE c.department_id = :dept AND c.status IN " + OPEN + " AND c.due_at < now()", params);
        return new OfficerDashboard(totals(mine, params), sla(mine, params), byStatus(mine, params), deptUnassigned,
                deptOverdue, trend(mine, params, 30));
    }

    public CitizenDashboard citizen(UserPrincipal p) {
        var params = new MapSqlParameterSource("uid", p.id());
        String scope = "c.citizen_id = :uid";
        return new CitizenDashboard(totals(scope, params), byStatus(scope, params));
    }

    public List<CategoryRow> slaByCategory(LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource()
                .addValue("from", from == null ? LocalDate.of(2000, 1, 1) : from)
                .addValue("to", (to == null ? LocalDate.now().plusDays(1) : to.plusDays(1)));
        String sql = """
                SELECT cat.id, cat.name, d.name AS dept, cat.sla_hours,
                       count(c.id) AS total,
                       count(c.resolved_at) AS resolved,
                       count(*) FILTER (WHERE c.resolved_at IS NOT NULL AND c.resolved_at <= c.due_at) AS within,
                       avg(EXTRACT(EPOCH FROM (c.resolved_at - c.created_at)) / 3600.0) FILTER (WHERE c.resolved_at IS NOT NULL) AS avg_h,
                       count(*) FILTER (WHERE c.status IN %s AND c.due_at < now()) AS overdue
                FROM complaint_categories cat
                JOIN departments d ON d.id = cat.department_id
                LEFT JOIN complaints c ON c.category_id = cat.id
                     AND c.created_at >= CAST(:from AS date) AND c.created_at < CAST(:to AS date)
                GROUP BY cat.id, cat.name, d.name, cat.sla_hours
                ORDER BY d.name, cat.name
                """.formatted(OPEN);
        return jdbc.query(sql, params, (rs, i) -> {
            long resolved = rs.getLong("resolved");
            long within = rs.getLong("within");
            return new CategoryRow(rs.getLong("id"), rs.getString("name"), rs.getString("dept"), rs.getInt("sla_hours"),
                    rs.getLong("total"), resolved, within, pct(within, resolved), round(rs.getBigDecimal("avg_h")),
                    rs.getLong("overdue"));
        });
    }

    // ------------------------------------------------------------------ building blocks

    private Totals totals(String scope, MapSqlParameterSource params) {
        String sql = """
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE c.status IN %1$s) AS open,
                       count(*) FILTER (WHERE c.status IN %1$s AND c.due_at < now()) AS overdue,
                       count(c.resolved_at) AS resolved,
                       count(*) FILTER (WHERE c.status = 'CLOSED') AS closed,
                       count(*) FILTER (WHERE c.status = 'REJECTED') AS rejected,
                       count(*) FILTER (WHERE c.status = 'NEEDS_INFORMATION') AS needs_info,
                       count(*) FILTER (WHERE c.status IN %1$s AND c.assigned_officer_id IS NULL) AS unassigned,
                       count(*) FILTER (WHERE c.status IN %1$s AND c.escalation_level > 0) AS escalated
                FROM complaints c WHERE %2$s
                """.formatted(OPEN, scope);
        return jdbc.queryForObject(sql, params, (rs, i) -> new Totals(rs.getLong("total"), rs.getLong("open"),
                rs.getLong("overdue"), rs.getLong("resolved"), rs.getLong("closed"), rs.getLong("rejected"),
                rs.getLong("needs_info"), rs.getLong("unassigned"), rs.getLong("escalated")));
    }

    private Sla sla(String scope, MapSqlParameterSource params) {
        String sql = """
                SELECT count(c.resolved_at) AS resolved,
                       count(*) FILTER (WHERE c.resolved_at IS NOT NULL AND c.resolved_at <= c.due_at) AS within,
                       avg(EXTRACT(EPOCH FROM (c.resolved_at - c.created_at)) / 3600.0) AS avg_h,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (c.resolved_at - c.created_at)) / 3600.0) AS median_h
                FROM complaints c WHERE c.resolved_at IS NOT NULL AND %s
                """.formatted(scope);
        return jdbc.queryForObject(sql, params, (rs, i) -> {
            long resolved = rs.getLong("resolved");
            long within = rs.getLong("within");
            Object median = rs.getObject("median_h");
            return new Sla(resolved, within, pct(within, resolved), round(rs.getBigDecimal("avg_h")),
                    median == null ? null : round(BigDecimal.valueOf(((Number) median).doubleValue())));
        });
    }

    private List<Count> byStatus(String scope, MapSqlParameterSource params) {
        return jdbc.query("SELECT c.status AS k, count(*) AS n FROM complaints c WHERE " + scope + " GROUP BY c.status ORDER BY n DESC",
                params, (rs, i) -> new Count(rs.getString("k"), rs.getLong("n")));
    }

    private List<Count> byPriority(String scope, MapSqlParameterSource params) {
        return jdbc.query("SELECT c.priority AS k, count(*) AS n FROM complaints c WHERE c.status IN " + OPEN + " AND " + scope
                        + " GROUP BY c.priority",
                params, (rs, i) -> new Count(rs.getString("k"), rs.getLong("n")));
    }

    private List<TrendPoint> trend(String scope, MapSqlParameterSource params, int days) {
        int d = Math.max(7, Math.min(days, 180));
        params.addValue("days", d - 1);
        String sql = """
                WITH days AS (
                    SELECT generate_series(CAST(now() AT TIME ZONE 'UTC' AS date) - CAST(:days AS int),
                                           CAST(now() AT TIME ZONE 'UTC' AS date), interval '1 day')::date AS day)
                SELECT days.day,
                       (SELECT count(*) FROM complaints c WHERE %1$s AND CAST(c.created_at AT TIME ZONE 'UTC' AS date) = days.day) AS submitted,
                       (SELECT count(*) FROM complaints c WHERE %1$s AND CAST(c.resolved_at AT TIME ZONE 'UTC' AS date) = days.day) AS resolved
                FROM days ORDER BY days.day
                """.formatted(scope);
        return jdbc.query(sql, params, (rs, i) -> new TrendPoint(rs.getDate("day").toLocalDate(),
                rs.getLong("submitted"), rs.getLong("resolved")));
    }

    private List<DepartmentRow> departments() {
        String sql = """
                SELECT d.id, d.name,
                       count(c.id) AS total,
                       count(*) FILTER (WHERE c.status IN %1$s) AS open,
                       count(*) FILTER (WHERE c.status IN %1$s AND c.due_at < now()) AS overdue,
                       count(c.resolved_at) AS resolved,
                       count(*) FILTER (WHERE c.resolved_at IS NOT NULL AND c.resolved_at <= c.due_at) AS within,
                       avg(EXTRACT(EPOCH FROM (c.resolved_at - c.created_at)) / 3600.0) FILTER (WHERE c.resolved_at IS NOT NULL) AS avg_h
                FROM departments d LEFT JOIN complaints c ON c.department_id = d.id
                GROUP BY d.id, d.name ORDER BY total DESC, d.name
                """.formatted(OPEN);
        return jdbc.query(sql, new MapSqlParameterSource(), (rs, i) -> new DepartmentRow(rs.getLong("id"), rs.getString("name"),
                rs.getLong("total"), rs.getLong("open"), rs.getLong("overdue"), rs.getLong("resolved"),
                pct(rs.getLong("within"), rs.getLong("resolved")), round(rs.getBigDecimal("avg_h"))));
    }

    private List<OfficerRow> officers() {
        String sql = """
                SELECT u.id, u.full_name, d.name AS dept,
                       count(*) FILTER (WHERE c.status IN %1$s) AS open,
                       count(*) FILTER (WHERE c.status IN %1$s AND c.due_at < now()) AS overdue,
                       count(*) FILTER (WHERE c.resolved_at >= now() - interval '30 days') AS resolved30,
                       avg(EXTRACT(EPOCH FROM (c.resolved_at - c.created_at)) / 3600.0) FILTER (WHERE c.resolved_at IS NOT NULL) AS avg_h
                FROM users u
                LEFT JOIN departments d ON d.id = u.department_id
                LEFT JOIN complaints c ON c.assigned_officer_id = u.id
                WHERE u.role = 'OFFICER' AND u.active
                GROUP BY u.id, u.full_name, d.name
                ORDER BY open DESC, u.full_name
                """.formatted(OPEN);
        return jdbc.query(sql, new MapSqlParameterSource(), (rs, i) -> new OfficerRow(rs.getLong("id"), rs.getString("full_name"),
                rs.getString("dept"), rs.getLong("open"), rs.getLong("overdue"), rs.getLong("resolved30"),
                round(rs.getBigDecimal("avg_h"))));
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n == null ? 0 : n;
    }

    static Double pct(long part, long whole) {
        if (whole == 0) return null;
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static Double round(BigDecimal v) {
        return v == null ? null : v.setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
