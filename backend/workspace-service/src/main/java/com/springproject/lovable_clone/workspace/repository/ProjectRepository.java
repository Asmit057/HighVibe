package com.springproject.lovable_clone.workspace.repository;

import com.springproject.lovable_clone.common.enums.ProjectRole;
import com.springproject.lovable_clone.workspace.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** Projects where the user is an accepted member, most recently touched first. */
    @Query("""
            SELECT p FROM Project p
            WHERE p.deletedAt IS NULL
            AND EXISTS (
                SELECT 1 FROM ProjectMember pm
                WHERE pm.id.userId = :userId AND pm.id.projectId = p.id AND pm.acceptedAt IS NOT NULL
            )
            ORDER BY p.updatedAt DESC
            """)
    List<Project> findAllAccessibleByUser(@Param("userId") Long userId);

    @Query("""
            SELECT p FROM Project p
            WHERE p.id = :projectId AND p.deletedAt IS NULL
            AND EXISTS (
                SELECT 1 FROM ProjectMember pm
                WHERE pm.id.userId = :userId AND pm.id.projectId = :projectId AND pm.acceptedAt IS NOT NULL
            )
            """)
    Optional<Project> findAccessibleProjectById(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Query("""
            SELECT COUNT(p) FROM Project p
            WHERE p.deletedAt IS NULL
            AND EXISTS (
                SELECT 1 FROM ProjectMember pm
                WHERE pm.id.userId = :userId AND pm.id.projectId = p.id AND pm.projectRole = :role
            )
            """)
    long countActiveProjectsWithRole(@Param("userId") Long userId, @Param("role") ProjectRole role);

    Optional<Project> findByIdAndDeletedAtIsNull(Long id);
}
