package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.Gift;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GiftRepository extends JpaRepository<Gift, UUID> {
}
