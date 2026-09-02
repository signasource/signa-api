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

    @Column private String animationUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sign_language_id", nullable = false)
    private SignLanguage signLanguage;
}
