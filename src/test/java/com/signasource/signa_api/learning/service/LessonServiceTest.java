package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.LessonDetailResponse;
import com.signasource.signa_api.learning.dto.LessonBlockResponse;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

	@Mock
	private LessonRepository lessonRepository;

	@Mock
	private LessonBlockRepository lessonBlockRepository;

	@InjectMocks
	private LessonService lessonService;

	private UUID lessonId;
	private Lesson lesson;
	private LessonBlock theoryBlock;
	private LessonBlock exerciseBlock;

	@BeforeEach
	void setUp() {
		lessonId = UUID.randomUUID();

		lesson = Lesson.builder().id(lessonId).code("LSA-L01").name("Introducción al Alfabeto")
				.description("Aprende las primeras letras").order(1).build();

		theoryBlock = LessonBlock.builder().id(UUID.randomUUID()).type(BlockType.THEORY).order(1)
				.config("{\"text\": \"El alfabeto dactilológico...\"}").xpReward(10).isExamEligible(false)
				.lesson(lesson).build();

		exerciseBlock = LessonBlock.builder().id(UUID.randomUUID()).type(BlockType.EXERCISE_ATTEMPT).order(2)
				.config("{\"expected_sign\": \"A\"}").xpReward(50).isExamEligible(true).lesson(lesson).build();
	}

	@Test
	void shouldReturnLessonDetailWithOrderedBlocks() {
		when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
		when(lessonBlockRepository.findByLessonIdOrderByOrderAsc(lessonId))
				.thenReturn(List.of(theoryBlock, exerciseBlock));

		LessonDetailResponse response = lessonService.getLessonContent(lessonId);

		assertNotNull(response);
		assertEquals(lessonId, response.id());
		assertEquals("Introducción al Alfabeto", response.name());
		assertEquals(2, response.blocks().size());

		LessonBlockResponse firstBlock = response.blocks().get(0);
		assertEquals("THEORY", firstBlock.type());
		assertEquals(1, firstBlock.order());
		assertEquals(10, firstBlock.xpReward());

		LessonBlockResponse secondBlock = response.blocks().get(1);
		assertEquals("EXERCISE_ATTEMPT", secondBlock.type());
		assertTrue(secondBlock.isExamEligible());

		verify(lessonRepository).findById(lessonId);
		verify(lessonBlockRepository).findByLessonIdOrderByOrderAsc(lessonId);
	}

	@Test
	void shouldThrowNotFoundWhenLessonDoesNotExist() {
		when(lessonRepository.findById(lessonId)).thenReturn(Optional.empty());

		NotFoundException exception = assertThrows(NotFoundException.class,
				() -> lessonService.getLessonContent(lessonId));

		assertTrue(exception.getMessage().contains("Lección no encontrada"));
		verify(lessonRepository).findById(lessonId);
		verifyNoInteractions(lessonBlockRepository);
	}
}
