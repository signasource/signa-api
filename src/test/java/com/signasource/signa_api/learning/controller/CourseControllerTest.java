package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.dto.CourseDetailResponse;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.service.CourseService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

    @Mock private CourseService courseService;

    @InjectMocks private CourseController courseController;

    private final UUID signLanguageId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    @Test
    void testGetCoursesCatalog() {
        Pageable pageable = PageRequest.of(0, 10);
        CourseSummaryResponse summary =
                new CourseSummaryResponse(courseId, "LSA Básico", "Desc", "url", true, "LSA");
        Page<CourseSummaryResponse> mockPage = new PageImpl<>(List.of(summary));

        when(courseService.getCoursesCatalog(eq(signLanguageId), any(Pageable.class)))
                .thenReturn(mockPage);

        ResponseEntity<Page<CourseSummaryResponse>> response =
                courseController.getCoursesCatalog(signLanguageId, pageable);

        verify(courseService).getCoursesCatalog(signLanguageId, pageable);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getContent().size());
    }

    @Test
    void testGetCourseDetail() {
        CourseDetailResponse mockDetail =
                new CourseDetailResponse(courseId, "LSA Básico", "Desc", "url", "v1.0", List.of());

        when(courseService.getCourseDetail(courseId)).thenReturn(mockDetail);

        ResponseEntity<CourseDetailResponse> response = courseController.getCourseDetail(courseId);

        verify(courseService).getCourseDetail(courseId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockDetail, response.getBody());
    }
}
