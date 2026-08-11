package com.signasource.signa_api.learning.repository.projection;

import java.util.UUID;

/** Total number of lessons per topic, grouped for a set of course versions. */
public interface TopicLessonTotalView {

    UUID getCourseVersionId();

    UUID getTopicId();

    long getTotalLessons();
}
