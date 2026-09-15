package com.signasource.signa_api.content.service;

import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.PracticeAttemptRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.learning.repository.UserTopicProgressRepository;
import org.springframework.stereotype.Component;

/**
 * Deletes content that no longer exists in the YAML, along with whatever points at it.
 *
 * <p>The user rows are removed first and on purpose. Progress and attempts reference content by id,
 * so leaving them behind blocks the delete with a foreign key; and keeping them would describe an
 * exercise nobody can reach any more. Content that stays in the YAML is never touched here, which
 * is what keeps an ordinary content edit from costing anybody their progress.
 */
@Component
public class ContentPurger {

    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final UserTopicProgressRepository topicProgressRepository;
    private final UserLessonProgressRepository lessonProgressRepository;
    private final LessonBlockAttemptRepository blockAttemptRepository;
    private final PracticeAttemptRepository practiceAttemptRepository;

    public ContentPurger(
            TopicRepository topicRepository,
            LessonRepository lessonRepository,
            LessonBlockRepository lessonBlockRepository,
            UserTopicProgressRepository topicProgressRepository,
            UserLessonProgressRepository lessonProgressRepository,
            LessonBlockAttemptRepository blockAttemptRepository,
            PracticeAttemptRepository practiceAttemptRepository) {
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.lessonBlockRepository = lessonBlockRepository;
        this.topicProgressRepository = topicProgressRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.blockAttemptRepository = blockAttemptRepository;
        this.practiceAttemptRepository = practiceAttemptRepository;
    }

    public void purgeTopic(Topic topic) {
        topic.getLessons().forEach(this::detachLesson);
        topicProgressRepository.deleteByTopicId(topic.getId());
        topicRepository.delete(topic);
    }

    public void purgeLesson(Lesson lesson) {
        detachLesson(lesson);
        lessonRepository.delete(lesson);
    }

    public void purgeBlock(LessonBlock block) {
        detachBlock(block);
        lessonBlockRepository.delete(block);
    }

    /** Clears what points at a lesson without deleting it: its parent's cascade does that. */
    private void detachLesson(Lesson lesson) {
        lesson.getLessonBlocks().forEach(this::detachBlock);
        lessonProgressRepository.deleteByLessonId(lesson.getId());
    }

    private void detachBlock(LessonBlock block) {
        blockAttemptRepository.deleteByLessonBlockId(block.getId());
        practiceAttemptRepository.deleteByLessonBlockId(block.getId());
    }
}
