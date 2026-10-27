package com.grievance360.complaint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);

    Optional<Attachment> findByIdAndComplaintId(Long id, Long complaintId);

    long countByComplaintId(Long complaintId);
}
