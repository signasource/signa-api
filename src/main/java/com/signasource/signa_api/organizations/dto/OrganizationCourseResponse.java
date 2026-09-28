package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import java.time.Instant;

public record OrganizationCourseResponse(CourseSummaryResponse course, Instant contractedAt) {
    public static OrganizationCourseResponse from(OrganizationCourse organizationCourse) {
        return new OrganizationCourseResponse(
                CourseSummaryResponse.from(organizationCourse.getCourse()),
                organizationCourse.getContractedAt());
    }
}
