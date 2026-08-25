package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.UserDailyXp;
import com.signasource.signa_api.users.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDailyXpRepository extends JpaRepository<UserDailyXp, UUID> {

    Optional<UserDailyXp> findByUserAndXpDate(User user, LocalDate xpDate);

    List<UserDailyXp> findByUserAndXpDateBetween(User user, LocalDate start, LocalDate end);
}
