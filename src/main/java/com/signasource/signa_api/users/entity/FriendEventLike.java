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

/** Feed events are not stored, so a like points at its source row by type and id. */
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private FriendEventType eventType;

    @Column(name = "event_ref_id", nullable = false)
    private UUID eventRefId;

    /** Denormalised so the notification can be addressed without re-resolving the event. */
    @Column(name = "event_owner_id", nullable = false)
    private UUID eventOwnerId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
