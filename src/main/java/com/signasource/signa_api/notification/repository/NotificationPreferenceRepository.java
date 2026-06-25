package com.signasource.signa_api.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.signasource.signa_api.notification.entity.NotificationPreference;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

	Optional<NotificationPreference> findByUserId(UUID userId);

	List<NotificationPreference> findByDailyReminderEnabledTrue();
}
