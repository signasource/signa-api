package com.signasource.signa_api.learning.entity;

import com.signasource.signa_api.organizations.entity.Organization;
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

@Entity
@Table(name = "lesson_block_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonBlockAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_block_id", nullable = false)
    private LessonBlock lessonBlock;

    /** True/false for evaluable blocks, null for INFO blocks (view-only, no correctness). */
    @Column private Boolean isCorrect;

    /**
     * Set when the attempt was made through an enrollment granted by an organization, so activity
     * on contracted content can be told apart from the user's own.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(nullable = false, updatable = false)
    private Instant attemptedAt;

    @PrePersist
    protected void onCreate() {
        this.attemptedAt = Instant.now();
    }
}
