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
    void shouldNotImportWhenDisabled() {
        ContentImportRunner runner = new ContentImportRunner(importService, false);

        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(importService);
    }

    @Test
    void shouldImportAllOnStartupWhenEnabled() {
        when(importService.importAll())
                .thenReturn(
                        List.of(
                                new ContentImportOutcome(
                                        "LSA", "basic-course", ImportResult.CREATED)));
        ContentImportRunner runner = new ContentImportRunner(importService, true);

        runner.run(new DefaultApplicationArguments());

        verify(importService).importAll();
    }
}
