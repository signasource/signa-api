package com.signasource.signa_api.content.dto;

import java.util.List;

public record LessonDto(
        String code, String name, String description, List<LessonBlockDto> blocks) {}
