package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.PracticeAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, UUID> {

    long countByUserId(UUID userId);

    List<PracticeAttempt> findByUserIdOrderByAttemptedAtDesc(UUID userId);
}
