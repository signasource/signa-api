package com.signasource.signa_api.users.repository;

import com.signasource.signa_api.users.entity.UserSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
    Optional<UserSettings> findByUserId(UUID userId);
}
