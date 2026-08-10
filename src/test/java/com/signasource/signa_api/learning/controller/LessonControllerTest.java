package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.dto.LessonBlockResponse;
import com.signasource.signa_api.learning.dto.LessonDetailResponse;
import com.signasource.signa_api.learning.service.LessonService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class LessonControllerTest {

    @Mock private LessonService lessonService;

    @InjectMocks private LessonController lessonController;

    private final UUID lessonId = UUID.randomUUID();

    @Test
    void testGetLessonContent() {
        // Arrange
        LessonBlockResponse blockResponse =
                new LessonBlockResponse(
                        UUID.randomUUID(), "INFO", 1, "{\"text\":\"Aprende la letra A\"}", 20);

        LessonDetailResponse mockDetail =
                new LessonDetailResponse(
                        lessonId, "Aprende la letra A", "Desc", 1, List.of(blockResponse));

        when(lessonService.getLessonContent(lessonId)).thenReturn(mockDetail);

        // Act
        ResponseEntity<LessonDetailResponse> response = lessonController.getLessonContent(lessonId);

        // Assert
        verify(lessonService).getLessonContent(lessonId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(lessonId, response.getBody().id());
        assertEquals(1, response.getBody().blocks().size());
        assertEquals("INFO", response.getBody().blocks().get(0).type());
    }
}
