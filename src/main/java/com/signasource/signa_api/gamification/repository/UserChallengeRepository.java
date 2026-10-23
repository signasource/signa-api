package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.UserChallenge;
import com.signasource.signa_api.users.entity.User;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserChallengeRepository extends JpaRepository<UserChallenge, UUID> {

    @EntityGraph(attributePaths = "challenge")
    List<UserChallenge> findByUserAndPeriodStartIn(User user, Collection<LocalDate> periodStarts);

    @EntityGraph(attributePaths = "challenge")
    Optional<UserChallenge> findByIdAndUser(UUID id, User user);
}
