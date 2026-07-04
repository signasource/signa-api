package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.LessonBlockResponse;
import com.signasource.signa_api.learning.dto.LessonDetailResponse;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.repository.LessonRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;

    @Transactional(readOnly = true)
    public LessonDetailResponse getLessonContent(UUID lessonId) {

        Lesson lesson =
                lessonRepository
                        .findById(lessonId)
                        .orElseThrow(() -> new NotFoundException("Lesson not found"));

        List<LessonBlock> blocks = lesson.getLessonBlocks();

        List<LessonBlockResponse> blocksResponse =
                blocks.stream().map(LessonBlockResponse::from).toList();

        return LessonDetailResponse.from(lesson, blocksResponse);
    }
}
