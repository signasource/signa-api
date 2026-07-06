package com.signasource.signa_api.content.service;

import com.signasource.signa_api.content.importer.ContentImporter;
import com.signasource.signa_api.content.loader.ContentLoader;
import com.signasource.signa_api.content.loader.LoadedCourse;
import com.signasource.signa_api.content.validator.ContentValidator;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full content pipeline: load → validate → import. Validation runs before the
 * (transactional) import, so invalid content never reaches the database. This is the single entry
 * point callers should use; it guarantees the validation phase is not skipped.
 */
@Service
public class ContentImportService {

    private final ContentLoader loader;
    private final ContentValidator validator;
    private final ContentImporter importer;

    public ContentImportService(
            ContentLoader loader, ContentValidator validator, ContentImporter importer) {
        this.loader = loader;
        this.validator = validator;
        this.importer = importer;
    }

    /**
     * Loads, validates and imports a course.
     *
     * @throws com.signasource.signa_api.content.exception.ContentLoadException if the content
     *     cannot be loaded
     * @throws com.signasource.signa_api.content.exception.ContentValidationException if the content
     *     is structurally or semantically invalid (thrown before any persistence occurs)
     */
    public void importContent(String signLanguageCode, String courseCode) {
        LoadedCourse loaded = loader.load(signLanguageCode, courseCode);
        validator.validate(loaded);
        importer.importCourse(loaded);
    }
}
