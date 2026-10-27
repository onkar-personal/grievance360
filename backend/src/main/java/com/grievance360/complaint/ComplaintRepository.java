package com.grievance360.complaint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {

    @Override
    @EntityGraph(attributePaths = {"category", "department", "citizen", "assignedOfficer"})
    Page<Complaint> findAll(Specification<Complaint> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "department", "citizen", "assignedOfficer"})
    Optional<Complaint> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"category", "department"})
    Optional<Complaint> findByReferenceIgnoreCase(String reference);

    @Query(value = "SELECT nextval('complaint_ref_seq')", nativeQuery = true)
    long nextReferenceNumber();

    @EntityGraph(attributePaths = {"department", "assignedOfficer"})
    @Query("""
            SELECT c FROM Complaint c
            WHERE c.status IN :open AND c.dueAt < :now
              AND c.escalationLevel < :maxLevel
              AND (c.escalatedAt IS NULL OR c.escalatedAt < :repeatBefore)
            """)
    List<Complaint> findEscalationCandidates(@Param("open") Collection<ComplaintStatus> open, @Param("now") Instant now,
                                             @Param("maxLevel") int maxLevel, @Param("repeatBefore") Instant repeatBefore);

    @Query("SELECT c FROM Complaint c WHERE c.status = com.grievance360.complaint.ComplaintStatus.RESOLVED AND c.resolvedAt < :before")
    List<Complaint> findResolvedBefore(@Param("before") Instant before);
}
