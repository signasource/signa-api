package com.signasource.signa_api.users.repository;

import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserDailyActivity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDailyActivityRepository extends JpaRepository<UserDailyActivity, UUID> {

    Optional<UserDailyActivity> findByUserAndActivityDate(User user, LocalDate activityDate);
}
