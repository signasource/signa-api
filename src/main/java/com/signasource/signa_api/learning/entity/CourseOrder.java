package com.signasource.signa_api.learning.entity;

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
 * A user buying permanent access to a paid {@link Course} with Mercado Pago, one payment, mirroring
 * {@link com.signasource.signa_api.gamification.entity.GemOrder}. The price is frozen when the
 * order is created, so a later price change never alters what an open checkout pays. Access is
 * granted once, when Mercado Pago confirms the payment ({@link CourseOrderStatus#PAID}).
 */
@Data
@Entity
@Table(name = "course_orders")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseOrder {

    /**
     * Prefix of the Mercado Pago {@code external_reference}: tells course orders from gem orders
     * and organization seat purchases.
     */
    public static final String REFERENCE_PREFIX = "course_";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Course course;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CourseOrderStatus status = CourseOrderStatus.PENDING;

    /** Id of the Mercado Pago order that charges this one. */
    @Column(length = 100)
    private String mercadoPagoOrderId;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant paidAt;

    public String reference() {
        return REFERENCE_PREFIX + id;
    }
}
