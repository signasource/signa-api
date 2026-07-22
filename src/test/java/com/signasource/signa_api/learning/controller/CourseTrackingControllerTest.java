package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.ExerciseAttemptRequest;
import com.signasource.signa_api.learning.dto.LessonCompletionRequest;
import com.signasource.signa_api.learning.dto.TopicStatusRequest;
import com.signasource.signa_api.learning.entity.ProgressStatus;
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

    private UUID userId;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(userId);

        mockUserDetails = mock(CustomUserDetails.class);
        when(mockUserDetails.getUser()).thenReturn(mockUser);
    }

    @Test
    void enroll_ShouldReturn201() {
        UUID courseVersionId = UUID.randomUUID();

        ResponseEntity<Void> response =
                courseTrackingController.enroll(courseVersionId, mockUserDetails);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(trackingService).enrollUserInCourse(userId, courseVersionId);
    }

    @Test
    void updateTopicStatus_ShouldReturn200() {
        UUID topicId = UUID.randomUUID();
        TopicStatusRequest request = new TopicStatusRequest(ProgressStatus.COMPLETED);

        ResponseEntity<Void> response =
                courseTrackingController.updateTopicStatus(topicId, request, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(trackingService).updateTopicStatus(userId, topicId, ProgressStatus.COMPLETED);
    }

    @Test
    void completeLesson_ShouldReturn200() {
        UUID lessonId = UUID.randomUUID();
        LessonCompletionRequest request = new LessonCompletionRequest(100);

        ResponseEntity<Void> response =
                courseTrackingController.completeLesson(lessonId, request, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(trackingService).completeLesson(userId, lessonId, 100);
    }

    @Test
    void recordAttempt_ShouldReturn201() {
        UUID lessonBlockId = UUID.randomUUID();
        ExerciseAttemptRequest request = new ExerciseAttemptRequest(true);

        ResponseEntity<Void> response =
                courseTrackingController.recordAttempt(lessonBlockId, request, mockUserDetails);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(trackingService).recordExerciseAttempt(userId, lessonBlockId, true);
    }
}
