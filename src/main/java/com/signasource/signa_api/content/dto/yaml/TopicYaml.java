package com.signasource.signa_api.content.dto.yaml;

import java.util.List;

public record TopicYaml(TopicDto topic, List<LessonDto> lessons) {}
