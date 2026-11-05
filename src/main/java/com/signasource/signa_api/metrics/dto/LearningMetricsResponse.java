package com.signasource.signa_api.metrics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Global learning & content snapshot for the usage dashboard's "Aprendizaje & contenido" apartado:
 * per-course enrollment and completion, the enrollment funnel, the hardest lessons by accuracy,
 * vocabulary growth, the sign-report resolution surface and content-import health. State counts are
 * measured as of the end of the window; happenings (attempts, learned signs, imports) are counted
 * inside it. Sign-report figures are the current state (not windowed), matching the business
 * snapshot.
 */
public record LearningMetricsResponse(
        List<CourseStat> courses,
        List<CountItem> enrollmentFunnel,
        List<LessonAccuracy> hardestLessons,
        long learnedSigns,
        List<DailyCount> learnedSignsDaily,
        ReportsSection reports,
        List<CountItem> imports) {

    public record CountItem(String key, long count) {}

    public record DailyCount(LocalDate day, long count) {}

    public record CourseStat(
            String courseId,
            String courseName,
            long enrolled,
            long completed,
            long total,
            double completionPercent) {}

    public record LessonAccuracy(
            String lessonId,
            String lessonName,
            long evaluated,
            long correct,
            double accuracyPercent) {}

    public record MeaningCount(String meaning, long count) {}

    public record ReportsSection(
            long resolvedCount, double averageResolutionHours, List<MeaningCount> mostReported) {}
}
