package com.signasource.signa_api.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Entity
@Table(name = "user_settings")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "America/Argentina/Buenos_Aires";

    @Column(nullable = false)
    @Builder.Default
    private boolean notificationsEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Theme theme = Theme.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FontSize fontSize = FontSize.MEDIUM;

    @Column(nullable = false)
    @Builder.Default
    private boolean vibrationEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AccountVisibility accountVisibility = AccountVisibility.PUBLIC;

    @Column(nullable = false)
    @Builder.Default
    private int dailyGoalMinutes = 15;

    @Column(nullable = false)
    @Builder.Default
    private boolean dailyNotificationEnabled = false;

    @Column(nullable = false)
    @Builder.Default
    private LocalTime dailyNotificationTime = LocalTime.of(20, 0);

    @Column(nullable = false, length = 7)
    @Builder.Default
    private String profileHeaderColor = "#FFFFFF";
}
