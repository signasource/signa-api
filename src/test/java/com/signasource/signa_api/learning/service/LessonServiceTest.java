package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.signasource.signa_api.exceptions.ForbiddenException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.LessonBlockResponse;
import com.signasource.signa_api.learning.dto.LessonDetailResponse;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    @Mock private LessonRepository lessonRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;

    @InjectMocks private LessonService lessonService;

    private UUID lessonId;
    private UUID versionId;
    private User mockUser;
    private Lesson lesson;
    private LessonBlock theoryBlock;
    private LessonBlock exerciseBlock;

    @BeforeEach
    void setUp() {
        lessonId = UUID.randomUUID();
        versionId = UUID.randomUUID();

        mockUser = new User();
        mockUser.setId(UUID.randomUUID());

        CourseVersion courseVersion = CourseVersion.builder().id(versionId).build();
        Topic topic = Topic.builder().id(UUID.randomUUID()).courseVersion(courseVersion).build();

        lesson =
                Lesson.builder()
                        .id(lessonId)
                        .code("LSA-L01")
                        .name("Introducción al Alfabeto")
                        .description("Aprende las primeras letras")
                        .order(1)
                        .topic(topic)
                        .build();

        theoryBlock =
                LessonBlock.builder()
                        .id(UUID.randomUUID())
                        .type(BlockType.INFO)
                        .order(1)
                        .config("{\"text\": \"El alfabeto dactilológico...\"}")
                        .xpReward(10)
                        .lesson(lesson)
                        .build();

        exerciseBlock =
                LessonBlock.builder()
                        .id(UUID.randomUUID())
                        .type(BlockType.SELECT_MEANING)
                        .order(2)
                        .config("{\"expected_sign\": \"A\"}")
                        .xpReward(50)
                        .lesson(lesson)
                        .build();

        UserCourseEnrollment activeEnrollment = new UserCourseEnrollment();
        activeEnrollment.setStatus(EnrollmentStatus.ENROLLED);
        lenient()
                .when(enrollmentRepository.findByUserIdAndCourseVersionId(any(), any()))
                .thenReturn(Optional.of(activeEnrollment));
    }

    @Test
    void shouldReturnLessonDetailWithOrderedBlocks() {
        lesson.setLessonBlocks(List.of(theoryBlock, exerciseBlock));

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        LessonDetailResponse response = lessonService.getLessonContent(mockUser, lessonId);

        assertNotNull(response);
        assertEquals(lessonId, response.id());
        assertEquals("Introducción al Alfabeto", response.name());
        assertEquals(2, response.blocks().size());

        LessonBlockResponse firstBlock = response.blocks().get(0);
        assertEquals("INFO", firstBlock.type());
        assertEquals(1, firstBlock.order());
        assertEquals(10, firstBlock.xpReward());

        LessonBlockResponse secondBlock = response.blocks().get(1);
        assertEquals("SELECT_MEANING", secondBlock.type());

        verify(lessonRepository).findById(lessonId);
    }

    @Test
    void shouldThrowNotFoundWhenLessonDoesNotExist() {
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.empty());

        NotFoundException exception =
                assertThrows(
                        NotFoundException.class,
                        () -> lessonService.getLessonContent(mockUser, lessonId));

        assertTrue(exception.getMessage().contains("Lesson not found"));
        verify(lessonRepository).findById(lessonId);
    }

    @Test
    void shouldThrowForbiddenWhenUserIsNotEnrolledInLesson() {
        lesson.setLessonBlocks(List.of());
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(enrollmentRepository.findByUserIdAndCourseVersionId(mockUser.getId(), versionId))
                .thenReturn(Optional.empty());

        assertThrows(
                ForbiddenException.class, () -> lessonService.getLessonContent(mockUser, lessonId));
    }
}
