package com.signasource.signa_api.gamification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "shop_items")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShopItemType itemType;

    @Column(nullable = false)
    private int priceGems;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    /** For XP_MULTIPLIER and UNLIMITED_LIVES items: how long the effect lasts in minutes. */
    @Column private Integer durationMinutes;

    /** For XP_MULTIPLIER items: the multiplier value (e.g., 1.5 for 1.5x XP). */
    @Column private Double multiplierValue;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
