package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.UserTopicProgress;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTopicProgressRepository extends JpaRepository<UserTopicProgress, UUID> {

    Optional<UserTopicProgress> findByUserIdAndTopicId(UUID userId, UUID topicId);

    long countByUserIdAndTopicCourseVersionIdAndStatus(
            UUID userId, UUID courseVersionId, ProgressStatus status);
}
