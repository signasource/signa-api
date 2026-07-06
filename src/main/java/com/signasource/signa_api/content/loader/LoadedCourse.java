package com.signasource.signa_api.content.loader;

import com.signasource.signa_api.content.dto.CourseYaml;
import com.signasource.signa_api.content.dto.TopicYaml;
import java.util.List;

public record LoadedCourse(String signLanguageCode, CourseYaml course, List<TopicYaml> topics) {}
