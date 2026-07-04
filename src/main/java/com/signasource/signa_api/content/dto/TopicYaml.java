package com.signasource.signa_api.content.dto;

import java.util.List;

public record TopicYaml(TopicDto topic, List<LessonDto> lessons) {}
