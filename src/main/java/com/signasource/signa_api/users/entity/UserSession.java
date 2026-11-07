package com.signasource.signa_api.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One app-usage session reported by the client (foreground to background). Backs the "sesiones por
 * usuario" and "duración promedio de sesión" metrics; windowed by {@link #startedAt}.
 */
@Data
@Entity
@Table(name = "user_sessions")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    /** When the session began (client clock). */
    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private int durationSeconds;

    /** When the server received it (trusted clock), for auditing against client timestamps. */
    @Column(nullable = false)
    private Instant recordedAt;
}
