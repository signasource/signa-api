package com.signasource.signa_api.metrics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Global business snapshot for the usage dashboard: course/enrollment/progress state as of the end
 * of the window, accuracy and activity over the window, and the current error surface (pending sign
 * reports, failed content imports). Fine-grained throughput time series (enrollments, completions,
 * logins, …) come from the Prometheus proxy, not from here.
 */
public record BusinessMetricsResponse(
        CoursesSection courses,
        List<CountItem> enrollments,
        ProgressSection progress,
        UsersSection users,
        AccuracySection accuracy,
        ErrorsSection errors,
        List<DailyActivityPoint> activity) {

    /** A named count, e.g. a status bucket. */
    public record CountItem(String key, long count) {}

    public record CoursesSection(long total, List<CountItem> versionsByStatus) {}

    public record ProgressSection(long lessonsCompleted, long topicsCompleted) {}

    public record UsersSection(long total, long active) {}

    public record AccuracySection(
            long evaluated,
            long correct,
            double accuracyPercent,
            List<BlockTypeAccuracy> byBlockType) {}

    public record BlockTypeAccuracy(
            String type, long evaluated, long correct, double accuracyPercent) {}

    public record ErrorsSection(
            List<CountItem> signReportsByStatus, long pendingSignReports, long failedImports) {}

    public record DailyActivityPoint(
            LocalDate day, long attempts, long correct, long lessonsCompleted) {}
}
