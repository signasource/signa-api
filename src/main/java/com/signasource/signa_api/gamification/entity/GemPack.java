package com.signasource.signa_api.gamification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A bundle of gems sold for real money through the Google Play in-app product {@code productId}.
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

    /** Google Play in-app product id. Immutable once created in Play Console. */
    @Column(nullable = false, unique = true, length = 100)
    private String productId;

    @Column(nullable = false)
    private int gems;

    @Column(nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
