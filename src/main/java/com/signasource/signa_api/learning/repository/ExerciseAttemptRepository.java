package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.ExerciseAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExerciseAttemptRepository extends JpaRepository<ExerciseAttempt, UUID> {}
