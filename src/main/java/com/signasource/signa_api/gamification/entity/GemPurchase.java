package com.signasource.signa_api.gamification.entity;

import com.signasource.signa_api.users.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A verified Google Play purchase of a {@link GemPack}. A row exists only once the gems were
 * credited; the unique {@code purchaseToken} is what makes redemption idempotent.
 */
@Data
@Entity
@Table(name = "gem_purchases")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GemPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gem_pack_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private GemPack gemPack;

    @Column(nullable = false, length = 100)
    private String productId;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String purchaseToken;

    @Column(length = 100)
    private String orderId;

    @Column(nullable = false)
    private int gemsGranted;

    /** When Google recorded the payment. */
    @Column(nullable = false)
    private Instant purchasedAt;

    /** When this API verified the token and credited the gems. */
    @Column(nullable = false)
    private Instant verifiedAt;
}
