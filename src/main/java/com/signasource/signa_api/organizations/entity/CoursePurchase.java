package com.signasource.signa_api.organizations.entity;

import com.signasource.signa_api.learning.entity.Course;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Entity
@Table(name = "course_purchases")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoursePurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User createdBy;

    @Column(nullable = false)
    private int seats;

    @Column(nullable = false)
    private int durationDays;

    @Column(nullable = false)
    private int signCount;

    /** Price per seat before the volume discount. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal listPricePerSeat;

    @Column(nullable = false)
    private int discountPercent;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerSeat;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseStatus status = PurchaseStatus.PENDING_PAYMENT;

    @Column(length = 100)
    private String paymentReference;

    /** Last Mercado Pago Checkout Pro preference created for this purchase. */
    @Column(length = 100)
    private String mercadoPagoPreferenceId;

    @Column private Instant paidAt;

    @Column private Instant invitationDeadline;

    @Column private Instant cancelledAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "purchase")
    @OrderBy("email ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<PurchaseSeat> seatList = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
