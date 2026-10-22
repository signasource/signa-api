package com.signasource.signa_api.content.command;

import com.signasource.signa_api.content.entity.ContentImportRun;
import com.signasource.signa_api.content.entity.ContentImportTrigger;
import com.signasource.signa_api.content.service.ContentImportHistoryService;
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

    private final ContentImportHistoryService historyService;
    private final boolean importOnStartup;

    public ContentImportRunner(
            ContentImportHistoryService historyService,
            @Value("${app.content.import-on-startup:true}") boolean importOnStartup) {
        this.historyService = historyService;
        this.importOnStartup = importOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!importOnStartup) {
            log.info("Content import on startup disabled (app.content.import-on-startup=false)");
            return;
        }

        log.info("Validating and importing all content under content/");
        ContentImportRun run = historyService.run(ContentImportTrigger.STARTUP, null);
        log.info(
                "Startup content import recorded: {} ({} created, {} updated, {} unchanged)",
                run.getStatus(),
                run.getCreated(),
                run.getUpdated(),
                run.getUnchanged());
    }
}
