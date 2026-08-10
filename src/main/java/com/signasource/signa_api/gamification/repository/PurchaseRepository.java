package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.users.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    Optional<Purchase> findByIdAndUser(UUID id, User user);
}
