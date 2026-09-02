package com.signasource.signa_api.learning.repository.projection;

import java.util.UUID;

public interface TopicLessonTotalView {

    UUID getCourseVersionId();

    UUID getTopicId();

    long getTotalLessons();
}
