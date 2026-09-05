package com.signasource.signa_api.content.command;

import com.signasource.signa_api.content.dto.result.ContentImportOutcome;
import com.signasource.signa_api.content.service.ContentImportService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class ContentImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentImportRunner.class);

    private final ContentImportService importService;
    private final boolean importOnStartup;

    public ContentImportRunner(
            ContentImportService importService,
            @Value("${app.content.import-on-startup:true}") boolean importOnStartup) {
        this.importService = importService;
        this.importOnStartup = importOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!importOnStartup) {
            log.info("Content import on startup disabled (app.content.import-on-startup=false)");
            return;
        }

        log.info("Validating and importing all content under content/");
        List<ContentImportOutcome> outcomes = importService.importAll();

        if (outcomes.isEmpty()) {
            log.warn("No content found to import under content/");
            return;
        }
        for (ContentImportOutcome outcome : outcomes) {
            log.info(
                    "Imported content {}/{}: {}",
                    outcome.signLanguageCode(),
                    outcome.courseCode(),
                    outcome.result());
        }
    }
}
