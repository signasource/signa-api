package com.signasource.signa_api.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.content.dto.result.ContentImportOutcome;
import com.signasource.signa_api.content.dto.result.ImportResult;
import com.signasource.signa_api.content.entity.ContentImportRun;
import com.signasource.signa_api.content.entity.ContentImportStatus;
import com.signasource.signa_api.content.entity.ContentImportTrigger;
import com.signasource.signa_api.content.repository.ContentImportRunRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ContentImportHistoryServiceTest {

    @Mock private ContentImportService importService;
    @Mock private ContentImportRunRepository runRepository;
    @InjectMocks private ContentImportHistoryService service;

    @Test
    void shouldRecordSuccessfulRunWithAggregatedCounts() {
        when(importService.importAll())
                .thenReturn(
                        List.of(
                                new ContentImportOutcome("LSA", "a", ImportResult.CREATED),
                                new ContentImportOutcome("LSA", "b", ImportResult.CREATED),
                                new ContentImportOutcome("LSA", "c", ImportResult.UPDATED),
                                new ContentImportOutcome("LSA", "d", ImportResult.UNCHANGED)));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContentImportRun run = service.run(ContentImportTrigger.MANUAL, "admin@signa.com");

        ArgumentCaptor<ContentImportRun> captor = ArgumentCaptor.forClass(ContentImportRun.class);
        verify(runRepository).save(captor.capture());
        ContentImportRun saved = captor.getValue();
        assertSame(saved, run);
        assertEquals(ContentImportStatus.SUCCESS, saved.getStatus());
        assertEquals(ContentImportTrigger.MANUAL, saved.getTrigger());
        assertEquals("admin@signa.com", saved.getTriggeredBy());
        assertEquals(4, saved.getCoursesProcessed());
        assertEquals(2, saved.getCreated());
        assertEquals(1, saved.getUpdated());
        assertEquals(1, saved.getUnchanged());
        assertEquals(
                "LSA/a: CREATED\nLSA/b: CREATED\nLSA/c: UPDATED\nLSA/d: UNCHANGED",
                saved.getDetails());
    }

    @Test
    void shouldRecordFailedRunWithoutRethrowingWhenImportFails() {
        when(importService.importAll()).thenThrow(new RuntimeException("boom"));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContentImportRun run = service.run(ContentImportTrigger.STARTUP, null);

        assertEquals(ContentImportStatus.FAILED, run.getStatus());
        assertEquals(ContentImportTrigger.STARTUP, run.getTrigger());
        assertNull(run.getTriggeredBy());
        assertEquals(0, run.getCoursesProcessed());
        assertEquals(0, run.getCreated());
        assertEquals("boom", run.getDetails());
    }

    @Test
    void shouldReturnHistoryFromRepository() {
        Page<ContentImportRun> page = new PageImpl<>(List.of(new ContentImportRun()));
        when(runRepository.findAllByOrderByStartedAtDesc(Pageable.unpaged())).thenReturn(page);

        assertSame(page, service.history(Pageable.unpaged()));
    }
}
