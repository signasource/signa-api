package com.signasource.signa_api.learning.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.LessonBlockResponse;
import com.signasource.signa_api.learning.dto.LessonDetailResponse;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;

import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonService {

	private final LessonRepository lessonRepository;
	private final LessonBlockRepository lessonBlockRepository;

	@Transactional(readOnly = true)
	public LessonDetailResponse getLessonContent(UUID lessonId) {

		Lesson lesson = lessonRepository.findById(lessonId)
				.orElseThrow(() -> new NotFoundException("Lección no encontrada"));

		List<LessonBlock> blocks = lessonBlockRepository.findByLessonIdOrderByOrderAsc(lessonId);

		List<LessonBlockResponse> blocksResponse = blocks.stream()
				.map(block -> new LessonBlockResponse(block.getId(), block.getType().name(), block.getOrder(),
						block.getConfig(), block.getXpReward(), block.isExamEligible()))
				.toList();

		return new LessonDetailResponse(lesson.getId(), lesson.getName(), lesson.getDescription(), lesson.getOrder(),
				blocksResponse);
	}
}
