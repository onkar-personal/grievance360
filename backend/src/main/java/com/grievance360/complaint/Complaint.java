package com.grievance360.complaint;

import com.grievance360.department.ComplaintCategory;
import com.grievance360.department.Department;
import com.grievance360.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "complaints")
@Getter
@Setter
@NoArgsConstructor
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String reference;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ComplaintCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false, updatable = false)
    private User citizen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_officer_id")
    private User assignedOfficer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Column(name = "location_address")
    private String locationAddress;
    @Column(name = "location_ward")
    private String locationWard;
    @Column(name = "location_city")
    private String locationCity;
    @Column(name = "location_pincode")
    private String locationPincode;
    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public boolean isOverdue(Instant now) {
        return status.isOpen() && dueAt != null && now.isAfter(dueAt);
    }
}
