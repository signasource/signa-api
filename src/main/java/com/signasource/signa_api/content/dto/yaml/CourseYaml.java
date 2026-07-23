package com.signasource.signa_api.content.dto.yaml;

import java.util.List;

public record CourseYaml(CourseMetadataDto course, CourseVersionDto version, List<String> topics) {}
