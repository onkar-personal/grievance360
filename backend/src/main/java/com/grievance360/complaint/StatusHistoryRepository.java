package com.grievance360.complaint;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    @EntityGraph(attributePaths = "actor")
    List<StatusHistory> findByComplaintIdOrderByCreatedAtAscIdAsc(Long complaintId);

    /** Most recent entry that moved the complaint into the given status. */
    Optional<StatusHistory> findFirstByComplaintIdAndToStatusOrderByIdDesc(Long complaintId, ComplaintStatus toStatus);
}
