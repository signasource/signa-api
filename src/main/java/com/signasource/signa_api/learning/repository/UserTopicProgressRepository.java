package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.UserTopicProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTopicProgressRepository extends JpaRepository<UserTopicProgress, UUID> {

    List<UserTopicProgress> findByUserId(UUID userId);

    Optional<UserTopicProgress> findByUserIdAndTopicId(UUID userId, UUID topicId);

    List<UserTopicProgress> findByUserIdAndTopicCourseVersionId(UUID userId, UUID courseVersionId);
}
