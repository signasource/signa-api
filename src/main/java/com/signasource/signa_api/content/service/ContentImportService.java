package com.signasource.signa_api.content.service;

import com.signasource.signa_api.content.dto.load.CourseRef;
import com.signasource.signa_api.content.dto.load.LoadedCourse;
import com.signasource.signa_api.content.dto.result.ContentImportOutcome;
import com.signasource.signa_api.content.dto.result.ImportResult;
import com.signasource.signa_api.content.validator.ContentValidator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the content pipeline: load → validate → import. Validation runs before the
 * transactional import, so invalid content never reaches the database.
 */
@Service
public class ContentImportService {

    private final ContentLoader loader;
    private final ContentValidator validator;
    private final ContentPersister persister;
    private final SignCatalogImporter signCatalogImporter;

    public ContentImportService(
            ContentLoader loader,
            ContentValidator validator,
            ContentPersister persister,
            SignCatalogImporter signCatalogImporter) {
        this.loader = loader;
        this.validator = validator;
        this.persister = persister;
        this.signCatalogImporter = signCatalogImporter;
    }

    @Transactional
    public List<ContentImportOutcome> importAll() {
        List<CourseRef> refs = loader.discover();

        List<LoadedCourse> loaded = new ArrayList<>();
        for (CourseRef ref : refs) {
            LoadedCourse course = loader.load(ref.signLanguageCode(), ref.courseCode());
            validator.validate(course);
            loaded.add(course);
        }

        List<ContentImportOutcome> outcomes = new ArrayList<>();
        List<LoadedCourse> changed = new ArrayList<>();
        for (LoadedCourse course : loaded) {
            ImportResult result = persister.importCourse(course);
            outcomes.add(
                    new ContentImportOutcome(
                            course.signLanguageCode(), course.course().course().code(), result));
            if (result != ImportResult.UNCHANGED) {
                changed.add(course);
            }
        }

        // Only courses whose YAML changed feed the sign catalog. An unchanged course's signs were
        // created when it was first imported, and since then the catalog may have been edited from
        // the admin panel: re-reading the YAML would resurrect a sign renamed there (the YAML still
        // has the old meaning) as an orphan duplicate.
        signCatalogImporter.importSigns(changed);

        return outcomes;
    }
}
