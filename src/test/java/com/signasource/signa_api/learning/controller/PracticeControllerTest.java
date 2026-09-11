package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.LearnedSignResponse;
import com.signasource.signa_api.learning.dto.LessonBlockResponse;
import com.signasource.signa_api.learning.dto.PracticeAttemptRequest;
import com.signasource.signa_api.learning.dto.PracticeMistakeResponse;
import com.signasource.signa_api.learning.dto.PracticeMistakeReviewCompletedResponse;
import com.signasource.signa_api.learning.dto.PracticeSummaryResponse;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.service.PracticeService;
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
class PracticeControllerTest {

    @Mock private PracticeService practiceService;

    @InjectMocks private PracticeController practiceController;

    private User mockUser;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockUser = mock(User.class);

        mockUserDetails = mock(CustomUserDetails.class);
        when(mockUserDetails.getUser()).thenReturn(mockUser);
    }

    @Test
    void getSummary_ShouldReturn200WithSummary() {
        PracticeSummaryResponse summary = new PracticeSummaryResponse(12, 34L);
        when(practiceService.getSummary(mockUser)).thenReturn(summary);

        ResponseEntity<PracticeSummaryResponse> response =
                practiceController.getSummary(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(summary, response.getBody());
    }

    @Test
    void getExercisesByType_ShouldReturn200WithBlocks() {
        List<LessonBlockResponse> blocks =
                List.of(new LessonBlockResponse(UUID.randomUUID(), "MATCH", 0, "{}", 10));
        when(practiceService.getExercisesByType(mockUser, BlockType.MATCH, 5)).thenReturn(blocks);

        ResponseEntity<List<LessonBlockResponse>> response =
                practiceController.getExercisesByType(BlockType.MATCH, 5, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(blocks, response.getBody());
    }

    @Test
    void getLearnedSigns_ShouldReturn200WithSigns() {
        List<LearnedSignResponse> signs = List.of(new LearnedSignResponse("Hola"));
        when(practiceService.getLearnedSigns(mockUser, 50)).thenReturn(signs);

        ResponseEntity<List<LearnedSignResponse>> response =
                practiceController.getLearnedSigns(50, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(signs, response.getBody());
    }

    @Test
    void getExercisesForSign_ShouldReturn200WithBlocks() {
        List<LessonBlockResponse> blocks =
                List.of(new LessonBlockResponse(UUID.randomUUID(), "SELECT_SIGN", 0, "{}", 5));
        when(practiceService.getExercisesForSign(mockUser, "Hola", 10)).thenReturn(blocks);

        ResponseEntity<List<LessonBlockResponse>> response =
                practiceController.getExercisesForSign("Hola", 10, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(blocks, response.getBody());
    }

    @Test
    void getMistakes_ShouldReturn200WithMistakes() {
        List<PracticeMistakeResponse> mistakes =
                List.of(new PracticeMistakeResponse(UUID.randomUUID(), "MATCH", 3));
        when(practiceService.getMistakes(mockUser, 20)).thenReturn(mistakes);

        ResponseEntity<List<PracticeMistakeResponse>> response =
                practiceController.getMistakes(20, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(mistakes, response.getBody());
    }

    @Test
    void getMistakeExercises_ShouldReturn200WithBlocks() {
        List<LessonBlockResponse> blocks =
                List.of(new LessonBlockResponse(UUID.randomUUID(), "MATCH", 0, "{}", 10));
        when(practiceService.getMistakeExercises(mockUser, 10)).thenReturn(blocks);

        ResponseEntity<List<LessonBlockResponse>> response =
                practiceController.getMistakeExercises(10, mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(blocks, response.getBody());
    }

    @Test
    void recordAttempt_ShouldReturn201() {
        UUID lessonBlockId = UUID.randomUUID();
        PracticeAttemptRequest request = new PracticeAttemptRequest(lessonBlockId, true);

        ResponseEntity<Void> response = practiceController.recordAttempt(request, mockUserDetails);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(practiceService).recordAttempt(mockUser, lessonBlockId, true);
    }

    @Test
    void completeMistakeReview_ShouldReturn200WithXpEarned() {
        when(practiceService.completeMistakeReview(mockUser)).thenReturn(20);

        ResponseEntity<PracticeMistakeReviewCompletedResponse> response =
                practiceController.completeMistakeReview(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new PracticeMistakeReviewCompletedResponse(20), response.getBody());
    }
}
