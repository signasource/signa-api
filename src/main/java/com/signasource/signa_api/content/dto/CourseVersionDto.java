package com.signasource.signa_api.content.dto;

import com.signasource.signa_api.learning.entity.VersionStatus;

public record CourseVersionDto(String version, VersionStatus status) {}
