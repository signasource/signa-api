package com.signasource.signa_api.learning.dto;

import java.util.List;

/** A lesson as listed in a course's public syllabus: its name and the signs it teaches. */
public record LessonPreviewResponse(String name, List<String> signs) {}
