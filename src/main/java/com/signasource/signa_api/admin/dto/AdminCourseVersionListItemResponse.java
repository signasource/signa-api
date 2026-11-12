package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminCourseVersionListItemResponse(
        UUID id,
        UUID courseId,
        String courseName,
        String version,
        String status,
        Instant publishedAt,
        String basedOnVersion,
        boolean isFree,
        BigDecimal price,
        int unitCount,
        int lessonCount,
        int blockCount,
        int signCount,
        Instant updatedAt,
        String updatedBy,
        String updatedByName,
        String updatedByAvatar,
        String color) {

    public static AdminCourseVersionListItemResponse from(CourseVersion v, User editor) {
        int units = v.getTopics().size();
        int lessons = v.getTopics().stream().mapToInt(t -> t.getLessons().size()).sum();
        int blocks =
                v.getTopics().stream()
                        .flatMap(t -> t.getLessons().stream())
                        .mapToInt(l -> l.getLessonBlocks().size())
                        .sum();
        int signs =
                (int)
                        v.getTopics().stream()
                                .flatMap(t -> t.getLessons().stream())
                                .flatMap(l -> l.getLessonBlocks().stream())
                                .filter(b -> b.getType() == BlockType.INTRODUCE_SIGN)
                                .count();
        String name = editor != null ? (editor.getName() + " " + editor.getLastName()).trim() : null;
        return new AdminCourseVersionListItemResponse(
                v.getId(),
                v.getCourse().getId(),
                v.getCourse().getName(),
                v.getVersion(),
                v.getStatus().name(),
                v.getPublishedAt(),
                v.getBasedOnVersion(),
                v.getCourse().isFree(),
                v.getCourse().getPrice(),
                units,
                lessons,
                blocks,
                signs,
                v.getUpdatedAt(),
                v.getUpdatedBy(),
                name,
                editor != null ? editor.getAvatar() : null,
                v.getColor());
    }
}
