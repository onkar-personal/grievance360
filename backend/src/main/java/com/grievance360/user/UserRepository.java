package com.grievance360.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    @EntityGraph(attributePaths = "department")
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "department")
    Optional<User> findWithDepartmentById(Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRoleAndActiveTrue(Role role);

    List<User> findByRoleAndActiveTrue(Role role);

    List<User> findByRoleAndDepartmentIdAndActiveTrueOrderByFullName(Role role, Long departmentId);

    long countByRoleAndDepartmentIdAndActiveTrue(Role role, Long departmentId);
}
