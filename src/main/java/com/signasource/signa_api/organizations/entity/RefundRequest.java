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
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A user or org admin's request for a refund on any purchase type (SEATS, GEMS, COURSE). The
 * linked purchase is tracked polymorphically via {@code purchaseType} + {@code purchaseId}; there
 * is no JPA foreign-key constraint because the purchase lives in different tables depending on the
 * type. The request does not trigger an automatic refund — it is a signal for the admin team to
 * review and act in Mercado Pago.
 */
@Data
@Entity
@Table(name = "refund_requests")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 10)
    private String purchaseType;

    @Column(nullable = false)
    private UUID purchaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundRequestReason reason;

    @Column(nullable = false, length = 50)
    private String channel;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(length = 2000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RefundRequestStatus status = RefundRequestStatus.PENDING;

    @Column(length = 200)
    private String resolvedByName;

    @Column
    private Instant resolvedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
