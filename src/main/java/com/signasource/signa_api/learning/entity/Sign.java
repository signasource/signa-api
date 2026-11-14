package com.signasource.signa_api.learning.entity;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "signs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150, unique = true)
    private String meaning;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Handedness handedness;

    /** Object key of the animation in the media bucket (e.g. {@code lsa/hola.glb}), not a URL. */
    @Column private String animationUrl;

    /** Object key of the side-view video, only for signs whose animation is an .mp4. */
    @Column private String lateralAnimationUrl;

    /** Last upload from the admin panel; used as the media URL cache-buster. */
    @Column private Instant animationUpdatedAt;

    @Column private String youtubeUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sign_language_id", nullable = false)
    private SignLanguage signLanguage;
}
