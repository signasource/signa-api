package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShopItemRepository extends JpaRepository<ShopItem, UUID> {

    @Query(
            "SELECT s FROM ShopItem s WHERE s.active = true AND "
                    + "(:itemType IS NULL OR s.itemType = :itemType) "
                    + "AND (:maxPrice IS NULL OR s.priceGems <= :maxPrice) "
                    + "ORDER BY s.priceGems ASC")
    List<ShopItem> findActiveWithFilters(
            @Param("itemType") ShopItemType itemType, @Param("maxPrice") Integer maxPrice);
}
