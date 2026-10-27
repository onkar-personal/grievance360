package com.grievance360.department;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<ComplaintCategory, Long> {
    @EntityGraph(attributePaths = "department")
    List<ComplaintCategory> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "department")
    List<ComplaintCategory> findByActiveTrueAndDepartmentActiveTrueOrderByNameAsc();

    @EntityGraph(attributePaths = "department")
    Optional<ComplaintCategory> findWithDepartmentById(Long id);

    boolean existsByDepartmentIdAndNameIgnoreCase(Long departmentId, String name);
}
