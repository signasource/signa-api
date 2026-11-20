package com.signasource.signa_api.organizations.entity;

import com.signasource.signa_api.users.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A Mercado Pago payment that was approved for a purchase but given back in full. {@code paymentId}
 * is unique: a repeated notification finds the row instead of refunding again.
 */
@Data
@Entity
@Table(name = "purchase_refunds")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRefund {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CoursePurchase purchase;

    @Column(nullable = false, unique = true, length = 30)
    private String paymentId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefundStatus status = RefundStatus.PENDING;

    @Column(length = 50)
    private String mercadoPagoRefundId;

    /** Why the organization cancelled, when it said (withdrawals only). */
    @Column(length = 500)
    private String note;

    /** Who cancelled from the panel (withdrawals only). A financial record: kept on erasure. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User requestedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column private Instant refundedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
