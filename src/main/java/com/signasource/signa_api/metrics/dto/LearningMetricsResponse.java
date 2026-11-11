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
        List<CountItem> imports,
        /** Lecciones iniciadas vs. completadas en la ventana, con su tasa de finalización. */
        LessonFlow lessons,
        /**
         * Ejercicios evaluados en la ventana: éxito e intentos promedio por (usuario, ejercicio).
         */
        ExerciseFlow exercises,
        /**
         * Onboarding steps (as of the window's close): registrados → … → completaron 2+ lecciones.
         */
        List<CountItem> onboardingFunnel,
        /** Lessons ranked by abandonment (started but not completed), worst first. */
        List<LessonDropoff> lessonRanking,
        /** Exercises (lesson blocks) with the highest error rate, worst first. */
        List<ExerciseDifficulty> highestErrorExercises,
        /** Exercises needing the most retries (attempts per distinct user), most first. */
        List<ExerciseDifficulty> mostRetriedExercises,
        /** Daily accuracy (correct / evaluated) over the window — the correct-answer evolution. */
        List<DailyAccuracy> dailyAccuracy,
        /** Signs ranked by error rate (highest first) across SELECT_MEANING/SELECT_SIGN blocks. */
        List<SignDifficulty> hardestSigns) {

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

    /**
     * Lessons started vs. completed in the window, with the immediately preceding equal-length
     * window for a period-over-period delta ({@code previous*} are {@code null} for the whole
     * history).
     */
    public record LessonFlow(
            long started,
            long completed,
            double completionPercent,
            Long previousStarted,
            Long previousCompleted) {}

    /**
     * Exercises evaluated in the window, with the previous equal-length window for a delta ({@code
     * previous*} are {@code null} for the whole history).
     */
    public record ExerciseFlow(
            long attempted,
            long correct,
            double successPercent,
            double averageAttempts,
            Long previousAttempted,
            Long previousCorrect) {}

    public record LessonDropoff(
            String lessonId,
            String lessonName,
            long started,
            long completed,
            double completionPercent,
            double abandonmentPercent) {}

    public record ExerciseDifficulty(
            String blockId,
            String label,
            long evaluated,
            long correct,
            double errorPercent,
            double averageAttempts) {}

    public record DailyAccuracy(
            LocalDate day, long evaluated, long correct, double accuracyPercent) {}

    /** A sign ranked by difficulty across all exercise blocks that test it. */
    public record SignDifficulty(
            String sign,
            long evaluated,
            long correct,
            double errorPercent,
            double averageAttempts) {}
}
