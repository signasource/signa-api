package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddOrganizationCourseRequest(@NotNull UUID courseId) {}
