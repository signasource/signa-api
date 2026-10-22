package com.signasource.signa_api.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Audit row for one execution of the content import pipeline, whether triggered on startup or by an
 * admin from the panel. Lets operators see what each import did (created/updated/unchanged counts)
 * and whether it failed, without reading the logs.
 */
@Entity
@Table(name = "content_import_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentImportTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentImportStatus status;

    /** Admin email for manual runs; {@code null} for startup runs. */
    private String triggeredBy;

    @Column(nullable = false)
    private int coursesProcessed;

    @Column(nullable = false)
    private int created;

    @Column(nullable = false)
    private int updated;

    @Column(nullable = false)
    private int unchanged;

    /** Per-course outcome lines on success, or the error message on failure. */
    @Column(columnDefinition = "text")
    private String details;
}
