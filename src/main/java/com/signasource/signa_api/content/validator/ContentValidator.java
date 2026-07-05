package com.signasource.signa_api.content.validator;

import com.signasource.signa_api.content.dto.CourseMetadataDto;
import com.signasource.signa_api.content.dto.CourseVersionDto;
import com.signasource.signa_api.content.dto.LessonBlockDto;
import com.signasource.signa_api.content.dto.LessonDto;
import com.signasource.signa_api.content.dto.TopicDto;
import com.signasource.signa_api.content.dto.TopicYaml;
import com.signasource.signa_api.content.exception.ContentValidationException;
import com.signasource.signa_api.content.loader.LoadedCourse;
import com.signasource.signa_api.content.validator.block.BlockValidator;
import com.signasource.signa_api.content.validator.block.ValidationContext;
import com.signasource.signa_api.content.validator.semantic.SemanticValidator;
import com.signasource.signa_api.content.validator.semantic.ValidationError;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ContentValidator {

    private final Map<BlockType, BlockValidator> blockValidators;
    private final List<SemanticValidator> semanticValidators;

    public ContentValidator(List<BlockValidator> blockValidators, List<SemanticValidator> semanticValidators) {
        this.blockValidators = blockValidators.stream()
                .collect(Collectors.toMap(BlockValidator::supports, v -> v));
        this.semanticValidators = semanticValidators;
    }

    public void validate(LoadedCourse loaded) {
        List<String> errors = new ArrayList<>();
        validateCourse(loaded, errors);
        for (SemanticValidator sv : semanticValidators) {
            List<ValidationError> semanticErrors = new ArrayList<>();
            sv.validate(loaded, semanticErrors);
            semanticErrors.forEach(e -> errors.add(e.location() + ": " + e.message()));
        }
        if (!errors.isEmpty()) {
            throw new ContentValidationException(errors);
        }
    }

    private void validateCourse(LoadedCourse loaded, List<String> errors) {
        CourseMetadataDto meta = loaded.course().course();
        CourseVersionDto version = loaded.course().version();

        requireNotBlank(meta.code(), "Course code is required", errors);
        requireNotBlank(meta.name(), "Course name is required", errors);
        requireNotBlank(version.version(), "Version is required", errors);

        List<TopicYaml> topics = loaded.topics();
        if (topics == null || topics.isEmpty()) {
            errors.add("Course has no topics");
        } else {
            validateTopics(topics, errors);
        }
    }

    private void validateTopics(List<TopicYaml> topics, List<String> errors) {
        checkDuplicates(
                topics.stream().map(t -> t.topic().code()).toList(),
                "Duplicate topic code",
                errors);
        for (TopicYaml topicYaml : topics) {
            validateTopic(topicYaml, errors);
        }
    }

    private void validateTopic(TopicYaml topicYaml, List<String> errors) {
        TopicDto topic = topicYaml.topic();
        String topicCode = topic.code();
        String ctx = topicContext(topic);

        requireNotBlank(topicCode, ctx + ": code is required", errors);
        requireNotBlank(topic.name(), ctx + ": name is required", errors);

        List<LessonDto> lessons = topicYaml.lessons();
        if (lessons == null || lessons.isEmpty()) {
            errors.add(ctx + " has no lessons");
        } else {
            validateLessons(lessons, topicCode, ctx, errors);
        }
    }

    private void validateLessons(
            List<LessonDto> lessons, String topicCode, String topicCtx, List<String> errors) {
        checkDuplicates(
                lessons.stream().map(LessonDto::code).toList(),
                topicCtx + ": duplicate lesson code",
                errors);
        for (LessonDto lesson : lessons) {
            validateLesson(lesson, topicCode, topicCtx, errors);
        }
    }

    private void validateLesson(
            LessonDto lesson, String topicCode, String topicCtx, List<String> errors) {
        String label =
                lesson.code() != null && !lesson.code().isBlank() ? lesson.code() : "(unknown)";
        String ctx = topicCtx + " > Lesson " + label;

        requireNotBlank(lesson.code(), ctx + ": code is required", errors);
        requireNotBlank(lesson.name(), ctx + ": name is required", errors);

        List<LessonBlockDto> blocks = lesson.blocks();
        if (blocks == null || blocks.isEmpty()) {
            errors.add(ctx + " has no blocks");
        } else {
            validateBlocks(blocks, topicCode, lesson.code(), errors);
        }
    }

    private void validateBlocks(
            List<LessonBlockDto> blocks, String topicCode, String lessonCode, List<String> errors) {
        for (int i = 0; i < blocks.size(); i++) {
            validateBlock(blocks.get(i), new ValidationContext(topicCode, lessonCode, i + 1), errors);
        }
    }

    private void validateBlock(LessonBlockDto block, ValidationContext ctx, List<String> errors) {
        if (block.type() == null) {
            errors.add(ctx.location() + ": type is required");
        }
        if (block.config() == null) {
            errors.add(ctx.location() + ": config is required");
        }
        if (block.xp() != null && block.xp() < 0) {
            errors.add(ctx.location() + ": xpReward must be >= 0 (got " + block.xp() + ")");
        }
        if (block.type() != null && block.config() != null) {
            BlockValidator validator = blockValidators.get(block.type());
            if (validator == null) {
                errors.add(ctx.location() + ": no validator registered for block type " + block.type());
            } else {
                validator.validate(block, ctx, errors);
            }
        }
    }

    private void requireNotBlank(String value, String message, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(message);
        }
    }

    private void checkDuplicates(List<String> codes, String errorPrefix, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank() && !seen.add(code)) {
                errors.add(errorPrefix + ": " + code);
            }
        }
    }

    private String topicContext(TopicDto topic) {
        String code = topic.code();
        return "Topic " + (code != null && !code.isBlank() ? code : "(unknown)");
    }
}
