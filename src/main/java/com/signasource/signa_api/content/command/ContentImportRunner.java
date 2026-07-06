package com.signasource.signa_api.content.command;

import com.signasource.signa_api.content.service.ContentImportService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Triggers a content import from the command line. Runs only when the {@code --import-content}
 * option is present, so normal application startup is unaffected. Each value has the form {@code
 * <signLanguageCode>/<courseCode>}, e.g.:
 *
 * <pre>java -jar signa-api.jar --import-content=LSA/basic-course</pre>
 *
 * <p>The option may be repeated to import several courses in one run. Any load/validation/import
 * failure propagates, failing startup with a non-zero exit code — appropriate for a build/ops
 * command.
 */
@Component
public class ContentImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentImportRunner.class);
    private static final String OPTION = "import-content";

    private final ContentImportService importService;

    public ContentImportRunner(ContentImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(OPTION)) {
            return;
        }

        List<String> targets = args.getOptionValues(OPTION);
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "--import-content requires a value of the form <signLanguageCode>/<courseCode>");
        }

        for (String target : targets) {
            String[] parts = target.split("/", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException(
                        "Invalid --import-content value '"
                                + target
                                + "'; expected <signLanguageCode>/<courseCode>");
            }
            String signLanguageCode = parts[0];
            String courseCode = parts[1];

            log.info("Importing content {}/{}", signLanguageCode, courseCode);
            importService.importContent(signLanguageCode, courseCode);
            log.info("Imported content {}/{} successfully", signLanguageCode, courseCode);
        }
    }
}
