package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.UserTopicProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTopicProgressRepository extends JpaRepository<UserTopicProgress, Long> {

    List<UserTopicProgress> findByUserId(Long userId);

    Optional<UserTopicProgress> findByUserIdAndTopicId(Long userId, Long topicId);
}
