package com.signasource.signa_api.content.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.signasource.signa_api.content.service.ContentImportService;
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
    void shouldImportWhenOptionPresent() {
        ContentImportRunner runner = new ContentImportRunner(importService);

        runner.run(new DefaultApplicationArguments("--import-content=LSA/basic-course"));

        verify(importService).importContent("LSA", "basic-course");
    }

    @Test
    void shouldImportEachValueWhenOptionRepeated() {
        ContentImportRunner runner = new ContentImportRunner(importService);

        runner.run(
                new DefaultApplicationArguments(
                        "--import-content=LSA/basic-course", "--import-content=ASL/intro"));

        verify(importService).importContent("LSA", "basic-course");
        verify(importService).importContent("ASL", "intro");
    }

    @Test
    void shouldThrowWhenValueHasNoCourse() {
        ContentImportRunner runner = new ContentImportRunner(importService);

        assertThatThrownBy(
                        () -> runner.run(new DefaultApplicationArguments("--import-content=LSA")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(importService);
    }
}
