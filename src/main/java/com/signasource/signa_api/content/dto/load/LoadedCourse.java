package com.signasource.signa_api.content.dto.load;

import com.signasource.signa_api.content.dto.yaml.CourseYaml;
import com.signasource.signa_api.content.dto.yaml.TopicYaml;
import java.util.List;

public record LoadedCourse(String signLanguageCode, CourseYaml course, List<TopicYaml> topics) {}
