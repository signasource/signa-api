package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.LessonBlockInteractionRequest;
import com.signasource.signa_api.learning.service.CourseTrackingService;
import com.signasource.signa_api.users.entity.User;
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
}
