package com.signasource.signa_api.organizations.dto;

import java.util.List;
import java.util.UUID;

public record CatalogCourseResponse(
        UUID id,
        String code,
        String name,
        String description,
        String coverUrl,
        int signCount,
        List<CatalogModuleResponse> modules) {}
