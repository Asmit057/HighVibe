package com.springproject.lovable_clone.workspace.repository;

import com.springproject.lovable_clone.common.enums.ProjectRole;
import com.springproject.lovable_clone.workspace.entity.ProjectMember;
import com.springproject.lovable_clone.workspace.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    List<ProjectMember> findByIdProjectIdOrderByInvitedAtAsc(Long projectId);

    /** Role of an accepted member; pending invitees have no role yet. */
    @Query("""
            SELECT pm.projectRole FROM ProjectMember pm
            WHERE pm.id.projectId = :projectId AND pm.id.userId = :userId AND pm.acceptedAt IS NOT NULL
            """)
    Optional<ProjectRole> findAcceptedRole(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Query("""
            SELECT pm FROM ProjectMember pm JOIN FETCH pm.project p
            WHERE pm.id.userId = :userId AND pm.acceptedAt IS NULL AND p.deletedAt IS NULL
            ORDER BY pm.invitedAt DESC
            """)
    List<ProjectMember> findPendingInvitations(@Param("userId") Long userId);

    Optional<ProjectMember> findFirstByIdProjectIdAndProjectRole(Long projectId, ProjectRole projectRole);
}
