package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopItemRepository extends JpaRepository<ShopItem, UUID> {

    List<ShopItem> findByActiveTrueOrderByPriceGemsAsc();

    List<ShopItem> findByItemTypeAndActiveTrueOrderByPriceGemsAsc(ShopItemType itemType);

    boolean existsByCode(String code);
}
