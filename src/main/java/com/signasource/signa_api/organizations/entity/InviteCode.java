package com.signasource.signa_api.organizations.entity;

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
import lombok.NoArgsConstructor;

/**
 * A code an organization hands out to its employees so they can join it and get the courses it
 * contracted. {@code maxUses} is {@code null} for an unlimited code (e.g. shared on a company
 * intranet); {@code expiresAt} is {@code null} for a code that never expires on its own.
 */
@Data
@Entity
@Table(name = "invite_codes")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** When set, only the user registered with this email can redeem the code (email invite). */
    @Column private String email;

    @Column private Instant expiresAt;

    @Column private Integer maxUses;

    /** Role the redeemer gets in the organization; ADMIN codes are panel-admin invitations. */
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) not null default 'MEMBER'")
    @Builder.Default
    private MemberRole memberRole = MemberRole.MEMBER;

    @Column(nullable = false)
    @Builder.Default
    private int useCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
