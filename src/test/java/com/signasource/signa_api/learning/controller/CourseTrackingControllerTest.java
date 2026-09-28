package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.CourseProgressResponse;
import com.signasource.signa_api.learning.dto.CourseRoadmapResponse;
import com.signasource.signa_api.learning.dto.EnrollmentSummaryResponse;
import com.signasource.signa_api.learning.dto.LessonBlockInteractionRequest;
import com.signasource.signa_api.learning.dto.SetCurrentCourseRequest;
import com.signasource.signa_api.learning.service.CourseRoadmapService;
import com.signasource.signa_api.learning.service.CourseTrackingService;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class CourseTrackingControllerTest {

    @Mock private CourseTrackingService trackingService;

    @Mock private CourseRoadmapService roadmapService;

    @InjectMocks private CourseTrackingController courseTrackingController;

    private User mockUser;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockUser = mock(User.class);

        mockUserDetails = mock(CustomUserDetails.class);
        when(mockUserDetails.getUser()).thenReturn(mockUser);
    }

    @Test
    void getMyProgress_ShouldReturn200WithProgress() {
        List<CourseProgressResponse> progress =
                List.of(new CourseProgressResponse("Basic LSA", null, 6, 2, 33, 0, null));
        when(trackingService.getUserCourseProgress(mockUser)).thenReturn(progress);

        ResponseEntity<List<CourseProgressResponse>> response =
                courseTrackingController.getMyProgress(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(progress, response.getBody());
        verify(trackingService).getUserCourseProgress(mockUser);
    }

    @Test
    void getCourseRoadmap_ShouldReturn200WithRoadmap() {
        UUID courseId = UUID.randomUUID();
        CourseRoadmapResponse roadmap =
                new CourseRoadmapResponse(courseId, "LSA Básico", "v1.0", List.of());
        when(roadmapService.getCourseRoadmap(mockUser, courseId)).thenReturn(roadmap);

        ResponseEntity<CourseRoadmapResponse> response =
                courseTrackingController.getCourseRoadmap(courseId, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(roadmap, response.getBody());
        verify(roadmapService).getCourseRoadmap(mockUser, courseId);
    }

    @Test
    void enroll_ShouldReturn201() {
        UUID courseVersionId = UUID.randomUUID();

        ResponseEntity<Void> response =
                courseTrackingController.enroll(courseVersionId, mockUserDetails);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(trackingService).enrollUserInCourse(mockUser, courseVersionId);
    }

    @Test
    void recordInteraction_ShouldReturn201_ForExerciseAttempt() {
        UUID lessonBlockId = UUID.randomUUID();
        LessonBlockInteractionRequest request = new LessonBlockInteractionRequest(true);

        ResponseEntity<Void> response =
                courseTrackingController.recordInteraction(lessonBlockId, request, mockUserDetails);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(trackingService).recordBlockInteraction(mockUser, lessonBlockId, true);
    }

    @Test
    void recordInteraction_ShouldReturn201_ForInfoBlockView() {
        UUID lessonBlockId = UUID.randomUUID();
        LessonBlockInteractionRequest request = new LessonBlockInteractionRequest(null);

        ResponseEntity<Void> response =
                courseTrackingController.recordInteraction(lessonBlockId, request, mockUserDetails);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(trackingService).recordBlockInteraction(mockUser, lessonBlockId, null);
    }

    @Test
    void getMyEnrollments_ShouldReturn200WithEnrollments() {
        List<EnrollmentSummaryResponse> enrollments =
                List.of(
                        new EnrollmentSummaryResponse(
                                UUID.randomUUID(), "Curso básico", null, null, null, true));
        when(trackingService.getUserEnrollments(mockUser)).thenReturn(enrollments);

        ResponseEntity<List<EnrollmentSummaryResponse>> response =
                courseTrackingController.getMyEnrollments(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(enrollments, response.getBody());
        verify(trackingService).getUserEnrollments(mockUser);
    }

    @Test
    void setCurrentCourse_ShouldReturn204() {
        UUID courseId = UUID.randomUUID();
        SetCurrentCourseRequest request = new SetCurrentCourseRequest(courseId);

        ResponseEntity<Void> response =
                courseTrackingController.setCurrentCourse(request, mockUserDetails);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(trackingService).setCurrentCourse(mockUser, courseId);
    }
}
