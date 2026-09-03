package com.signasource.signa_api.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A "me gusta" a user left on a friend's feed event.
 *
 * <p>Feed events are derived on the fly from {@code user_achievements} / {@code user_learned_signs}
 * rather than stored, so a like points at its source row through the pair ({@code eventType},
 * {@code eventRefId}).
 */
@Entity
@Table(
        name = "friend_event_likes",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_like_user_event",
                    columnNames = {"user_id", "event_type", "event_ref_id"})
        },
        indexes = {@Index(name = "idx_like_event", columnList = "event_type, event_ref_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendEventLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who left the like. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private FriendEventType eventType;

    /** Id of the source row the event was derived from. */
    @Column(name = "event_ref_id", nullable = false)
    private UUID eventRefId;

    /** The friend whose activity was liked, kept so the notification can be addressed. */
    @Column(name = "event_owner_id", nullable = false)
    private UUID eventOwnerId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
