package com.grievance360.department;

import com.grievance360.complaint.Priority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "complaint_categories")
@Getter
@Setter
@NoArgsConstructor
public class ComplaintCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "sla_hours", nullable = false)
    private int slaHours = 72;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", nullable = false)
    private Priority defaultPriority = Priority.MEDIUM;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
