package com.signasource.signa_api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(@NotBlank @Size(max = 150) String name) {}
