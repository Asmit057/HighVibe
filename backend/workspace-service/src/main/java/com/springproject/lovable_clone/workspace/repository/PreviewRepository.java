package com.springproject.lovable_clone.workspace.repository;

import com.springproject.lovable_clone.common.enums.PreviewStatus;
import com.springproject.lovable_clone.workspace.entity.Preview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PreviewRepository extends JpaRepository<Preview, Long> {

    Optional<Preview> findFirstByProjectIdAndStatusInOrderByCreatedAtDesc(Long projectId, Collection<PreviewStatus> statuses);

    Optional<Preview> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    long countByUserIdAndStatusIn(Long userId, Collection<PreviewStatus> statuses);

    List<Preview> findByStatusIn(Collection<PreviewStatus> statuses);

    List<Preview> findByStatusInAndExpiresAtBefore(Collection<PreviewStatus> statuses, Instant time);
}
