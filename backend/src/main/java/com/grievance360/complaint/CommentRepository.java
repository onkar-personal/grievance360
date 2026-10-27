package com.grievance360.complaint;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = "author")
    List<Comment> findByComplaintIdOrderByCreatedAtAscIdAsc(Long complaintId);

    @EntityGraph(attributePaths = "author")
    List<Comment> findByComplaintIdAndVisibilityOrderByCreatedAtAscIdAsc(Long complaintId, Comment.Visibility visibility);
}
