package com.signasource.signa_api.content.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.content.exception.ContentValidationException;
import com.signasource.signa_api.content.importer.ContentImporter;
import com.signasource.signa_api.content.loader.ContentLoader;
import com.signasource.signa_api.content.loader.LoadedCourse;
import com.signasource.signa_api.content.validator.ContentValidator;
import com.signasource.signa_api.content.validator.ValidationError;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentImportServiceTest {

    @Mock private ContentLoader loader;
    @Mock private ContentValidator validator;
    @Mock private ContentImporter importer;

    @Test
    void shouldLoadValidateThenImportInOrder() {
        LoadedCourse loaded = new LoadedCourse("LSA", null, List.of());
        when(loader.load("LSA", "basic-course")).thenReturn(loaded);

        ContentImportService service = new ContentImportService(loader, validator, importer);
        service.importContent("LSA", "basic-course");

        InOrder inOrder = inOrder(loader, validator, importer);
        inOrder.verify(loader).load("LSA", "basic-course");
        inOrder.verify(validator).validate(loaded);
        inOrder.verify(importer).importCourse(loaded);
    }

    @Test
    void shouldNotImportWhenValidationFails() {
        LoadedCourse loaded = new LoadedCourse("LSA", null, List.of());
        when(loader.load("LSA", "basic-course")).thenReturn(loaded);
        doThrow(new ContentValidationException(List.of(new ValidationError("Course", "boom"))))
                .when(validator)
                .validate(loaded);

        ContentImportService service = new ContentImportService(loader, validator, importer);

        assertThatThrownBy(() -> service.importContent("LSA", "basic-course"))
                .isInstanceOf(ContentValidationException.class);

        verify(importer, never()).importCourse(loaded);
    }
}
