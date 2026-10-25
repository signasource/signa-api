package com.signasource.signa_api.content.command;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.content.entity.ContentImportRun;
import com.signasource.signa_api.content.entity.ContentImportStatus;
import com.signasource.signa_api.content.entity.ContentImportTrigger;
import com.signasource.signa_api.content.service.ContentImportHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class ContentImportRunnerTest {

    @Mock private ContentImportHistoryService historyService;

    @Test
    void shouldNotImportWhenDisabled() {
        ContentImportRunner runner = new ContentImportRunner(historyService, false);

        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(historyService);
    }

    @Test
    void shouldRunStartupImportWhenEnabled() {
        when(historyService.run(ContentImportTrigger.STARTUP, null))
                .thenReturn(ContentImportRun.builder().status(ContentImportStatus.SUCCESS).build());
        ContentImportRunner runner = new ContentImportRunner(historyService, true);

        runner.run(new DefaultApplicationArguments());

        verify(historyService).run(ContentImportTrigger.STARTUP, null);
    }
}
