package com.signasource.signa_api.learning.dto;

/** Null for INFO blocks (view); true/false for evaluable blocks. */
public record LessonBlockInteractionRequest(Boolean isCorrect) {}
