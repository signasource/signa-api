package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.GemPack;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GemPackRepository extends JpaRepository<GemPack, UUID> {

    List<GemPack> findByActiveTrueOrderBySortOrderAsc();

    Optional<GemPack> findByProductIdAndActiveTrue(String productId);

    boolean existsByProductId(String productId);
}
