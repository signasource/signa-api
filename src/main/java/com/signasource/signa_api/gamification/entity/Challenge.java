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
@Table(name = "challenges")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Challenge {

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
    private ChallengeType challengeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChallengeCriteriaType criteriaType;

    @Column(nullable = false)
    private int criteriaValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChallengeRewardType rewardType;

    @Column(nullable = false)
    private int rewardQuantity;

    /** For XP_MULTIPLIER rewards: how long the effect lasts in minutes. */
    @Column private Integer rewardDurationMinutes;

    /** For XP_MULTIPLIER rewards: the multiplier value (e.g., 2.0 for 2x XP). */
    @Column private Double rewardMultiplierValue;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
