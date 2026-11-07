package com.signasource.signa_api.gamification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A bundle of gems sold for real money through Mercado Pago. The price lives here, in {@code
 * currency}; {@code productId} is only the pack's stable code.
 */
@Data
@Entity
@Table(name = "gem_packs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GemPack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Stable pack code (e.g. {@code gems_pack_300}). */
    @Column(nullable = false, unique = true, length = 100)
    private String productId;

    @Column(nullable = false)
    private int gems;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "ARS";

    @Column(nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
