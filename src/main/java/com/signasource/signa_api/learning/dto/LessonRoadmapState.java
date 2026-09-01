package com.signasource.signa_api.learning.dto;

/**
 * Presentation state of a lesson within the course roadmap, derived per user from {@code
 * UserLessonProgress} plus the lesson's position in the ordered sequence. Not persisted: it is a
 * view over {@link com.signasource.signa_api.learning.entity.ProgressStatus} where an untouched
 * lesson is {@code AVAILABLE} when the previous lesson is completed and {@code LOCKED} otherwise.
 */
public enum LessonRoadmapState {
    LOCKED,
    AVAILABLE,
    IN_PROGRESS,
    COMPLETED
}
