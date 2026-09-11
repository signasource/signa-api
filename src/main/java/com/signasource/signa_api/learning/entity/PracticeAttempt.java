package com.signasource.signa_api.learning.entity;

import com.signasource.signa_api.users.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A free-practice attempt at a lesson block, outside the real lesson flow. Kept separate from
 * {@link LessonBlockAttempt} so recording it never triggers XP, lives, or progress cascades.
 */
@Entity
@Table(name = "practice_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_block_id", nullable = false)
    private LessonBlock lessonBlock;

    @Column(nullable = false)
    private boolean isCorrect;

    @Column(nullable = false, updatable = false)
    private Instant attemptedAt;

    @PrePersist
    protected void onCreate() {
        this.attemptedAt = Instant.now();
    }
}
