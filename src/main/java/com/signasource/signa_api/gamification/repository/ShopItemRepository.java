package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.ShopItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopItemRepository extends JpaRepository<ShopItem, UUID> {}
