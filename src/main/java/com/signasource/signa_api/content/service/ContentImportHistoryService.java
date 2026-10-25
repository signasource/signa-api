package com.signasource.signa_api.content.service;

import com.signasource.signa_api.content.dto.result.ContentImportOutcome;
import com.signasource.signa_api.content.dto.result.ImportResult;
import com.signasource.signa_api.content.entity.ContentImportRun;
import com.signasource.signa_api.content.entity.ContentImportStatus;
import com.signasource.signa_api.content.entity.ContentImportTrigger;
import com.signasource.signa_api.content.repository.ContentImportRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the content import pipeline and records each execution as a {@link ContentImportRun}. The
 * import itself is transactional (see {@link ContentImportService#importAll()}); recording the run
 * happens outside that transaction so a failed import still leaves an audit row.
 *
 * <p>Failures are recorded and logged, never rethrown: a bad content commit must not take the API
 * down on startup, and the panel surfaces the failure through the run's {@code FAILED} status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentImportHistoryService {

    private final ContentImportService importService;
    private final ContentImportRunRepository runRepository;

    public ContentImportRun run(ContentImportTrigger trigger, String triggeredBy) {
        Instant startedAt = Instant.now();
        try {
            List<ContentImportOutcome> outcomes = importService.importAll();
            log.info("Content import ({}) succeeded: {} course(s)", trigger, outcomes.size());
            return save(
                    trigger, triggeredBy, startedAt, ContentImportStatus.SUCCESS, outcomes, null);
        } catch (RuntimeException e) {
            log.error("Content import ({}) failed: {}", trigger, e.getMessage(), e);
            return save(
                    trigger,
                    triggeredBy,
                    startedAt,
                    ContentImportStatus.FAILED,
                    List.of(),
                    e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<ContentImportRun> history(Pageable pageable) {
        return runRepository.findAllByOrderByStartedAtDesc(pageable);
    }

    private ContentImportRun save(
            ContentImportTrigger trigger,
            String triggeredBy,
            Instant startedAt,
            ContentImportStatus status,
            List<ContentImportOutcome> outcomes,
            String error) {
        ContentImportRun run =
                ContentImportRun.builder()
                        .startedAt(startedAt)
                        .finishedAt(Instant.now())
                        .trigger(trigger)
                        .status(status)
                        .triggeredBy(triggeredBy)
                        .coursesProcessed(outcomes.size())
                        .created((int) countByResult(outcomes, ImportResult.CREATED))
                        .updated((int) countByResult(outcomes, ImportResult.UPDATED))
                        .unchanged((int) countByResult(outcomes, ImportResult.UNCHANGED))
                        .details(status == ContentImportStatus.FAILED ? error : describe(outcomes))
                        .build();
        return runRepository.save(run);
    }

    private long countByResult(List<ContentImportOutcome> outcomes, ImportResult result) {
        return outcomes.stream().filter(o -> o.result() == result).count();
    }

    private String describe(List<ContentImportOutcome> outcomes) {
        return outcomes.stream()
                .map(o -> "%s/%s: %s".formatted(o.signLanguageCode(), o.courseCode(), o.result()))
                .collect(Collectors.joining("\n"));
    }
}
