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
        List<DailyAccuracy> dailyAccuracy) {

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

    public record LessonFlow(long started, long completed, double completionPercent) {}

    public record ExerciseFlow(
            long attempted, long correct, double successPercent, double averageAttempts) {}

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
}
