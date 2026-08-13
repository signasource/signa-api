package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    List<Purchase> findByUser(User user);
}
