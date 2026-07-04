package com.signasource.signa_api.content.dto;

public record CourseMetadataDto(
        String code, String name, String description, boolean free, String cover) {}
