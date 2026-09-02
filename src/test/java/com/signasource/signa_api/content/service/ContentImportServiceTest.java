package com.signasource.signa_api.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.content.dto.load.CourseRef;
import com.signasource.signa_api.content.dto.load.LoadedCourse;
import com.signasource.signa_api.content.dto.result.ContentImportOutcome;
import com.signasource.signa_api.content.dto.result.ImportResult;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.CourseMetadataDto;
import com.signasource.signa_api.content.dto.yaml.CourseYaml;
import com.signasource.signa_api.content.exception.ContentValidationException;
import com.signasource.signa_api.content.validator.ContentValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentImportServiceTest {

    @Mock private ContentLoader loader;
    @Mock private ContentValidator validator;
    @Mock private ContentPersister persister;
    @Mock private SignCatalogImporter signCatalogImporter;

    private ContentImportService service() {
        return new ContentImportService(loader, validator, persister, signCatalogImporter);
    }

    private LoadedCourse loadedWithCode(String code) {
        CourseYaml yaml =
                new CourseYaml(
                        new CourseMetadataDto(code, code, null, false, null), null, List.of());
        return new LoadedCourse("LSA", yaml, List.of());
    }

    @Test
    void shouldImportAllDiscoveredCourses() {
        LoadedCourse first = loadedWithCode("course-a");
        LoadedCourse second = loadedWithCode("course-b");
        when(loader.discover())
                .thenReturn(
                        List.of(
                                new CourseRef("LSA", "course-a"),
                                new CourseRef("LSA", "course-b")));
        when(loader.load("LSA", "course-a")).thenReturn(first);
        when(loader.load("LSA", "course-b")).thenReturn(second);
        when(persister.importCourse(first)).thenReturn(ImportResult.CREATED);
        when(persister.importCourse(second)).thenReturn(ImportResult.UNCHANGED);

        List<ContentImportOutcome> outcomes = service().importAll();

        assertThat(outcomes)
                .containsExactly(
                        new ContentImportOutcome("LSA", "course-a", ImportResult.CREATED),
                        new ContentImportOutcome("LSA", "course-b", ImportResult.UNCHANGED));
        verify(validator).validate(first);
        verify(validator).validate(second);
        verify(signCatalogImporter).importSigns(List.of(first, second));
    }

    @Test
    void shouldValidateEveryCourseBeforeImportingAny() {
        LoadedCourse first = loadedWithCode("course-a");
        LoadedCourse second = loadedWithCode("course-b");
        when(loader.discover())
                .thenReturn(
                        List.of(
                                new CourseRef("LSA", "course-a"),
                                new CourseRef("LSA", "course-b")));
        when(loader.load("LSA", "course-a")).thenReturn(first);
        when(loader.load("LSA", "course-b")).thenReturn(second);
        lenient()
                .doThrow(
                        new ContentValidationException(
                                List.of(new ValidationError("Course", "boom"))))
                .when(validator)
                .validate(second);

        assertThatThrownBy(() -> service().importAll())
                .isInstanceOf(ContentValidationException.class);

        verify(persister, never()).importCourse(first);
        verify(persister, never()).importCourse(second);
        verify(signCatalogImporter, never()).importSigns(anyList());
    }
}
