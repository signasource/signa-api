package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.CourseDetailResponse;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.service.CourseService;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

    @Mock private CustomUserDetails userDetails;

    @InjectMocks private CourseController courseController;

    private final UUID signLanguageId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).build();
        when(userDetails.getUser()).thenReturn(user);
    }

    @Test
    void testGetCoursesCatalog() {
        Pageable pageable = PageRequest.of(0, 10);
        CourseSummaryResponse summary =
                new CourseSummaryResponse(
                        courseId, "LSA Básico", "Desc", "url", true, null, false, "LSA");
        Page<CourseSummaryResponse> mockPage = new PageImpl<>(List.of(summary));

        when(courseService.getCoursesCatalog(eq(user), eq(signLanguageId), any(Pageable.class)))
                .thenReturn(mockPage);

        ResponseEntity<Page<CourseSummaryResponse>> response =
                courseController.getCoursesCatalog(userDetails, signLanguageId, pageable);

        verify(courseService).getCoursesCatalog(user, signLanguageId, pageable);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getContent().size());
    }

    @Test
    void testGetCourseDetail() {
        CourseDetailResponse mockDetail =
                new CourseDetailResponse(
                        courseId,
                        "Curso Pago",
                        "Desc",
                        "url",
                        false,
                        new BigDecimal("2900.00"),
                        false,
                        "v1.0",
                        0,
                        0,
                        0,
                        false,
                        List.of(),
                        null,
                        List.of());

        when(courseService.getCourseDetail(user, courseId)).thenReturn(mockDetail);

        ResponseEntity<CourseDetailResponse> response =
                courseController.getCourseDetail(userDetails, courseId);

        verify(courseService).getCourseDetail(user, courseId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockDetail, response.getBody());
    }
}
