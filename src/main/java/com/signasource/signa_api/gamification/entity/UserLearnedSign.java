package com.signasource.signa_api.gamification.entity;

import com.signasource.signa_api.learning.entity.CourseVersion;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Entity
@Table(
        name = "user_learned_signs",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"user_id", "sign", "course_version_id"}))
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLearnedSign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Column(nullable = false, length = 100)
    private String sign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_version_id", nullable = false)
    @ToString.Exclude
    private CourseVersion courseVersion;

    @Column(nullable = false)
    @Builder.Default
    private Instant learnedAt = Instant.now();
}
