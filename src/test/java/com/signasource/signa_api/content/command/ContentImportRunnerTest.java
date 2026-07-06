package com.signasource.signa_api.content.command;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.content.dto.result.ContentImportOutcome;
import com.signasource.signa_api.content.dto.result.ImportResult;
import com.signasource.signa_api.content.service.ContentImportService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class ContentImportRunnerTest {

    @Mock private ContentImportService importService;

    @Test
    void shouldDoNothingWhenOptionAbsent() {
        ContentImportRunner runner = new ContentImportRunner(importService);

        runner.run(new DefaultApplicationArguments("--server.port=0"));

        verifyNoInteractions(importService);
    }

    @Test
    void shouldImportAllWhenOptionPresent() {
        when(importService.importAll())
                .thenReturn(
                        List.of(
                                new ContentImportOutcome(
                                        "LSA", "basic-course", ImportResult.CREATED)));
        ContentImportRunner runner = new ContentImportRunner(importService);

        runner.run(new DefaultApplicationArguments("--import-content"));

        verify(importService).importAll();
    }

    @Test
    void shouldNotFailWhenNothingToImport() {
        when(importService.importAll()).thenReturn(List.of());
        ContentImportRunner runner = new ContentImportRunner(importService);

        runner.run(new DefaultApplicationArguments("--import-content"));

        verify(importService).importAll();
    }
}
