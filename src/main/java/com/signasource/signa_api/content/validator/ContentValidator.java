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

    private static final String COURSE = "Course";
    private static final String UNKNOWN = "(unknown)";

    private final Map<BlockType, BlockValidator> blockValidators;
    private final List<SemanticValidator> semanticValidators;

    public ContentValidator(
            List<BlockValidator> blockValidators, List<SemanticValidator> semanticValidators) {
        this.blockValidators =
                blockValidators.stream()
                        .collect(Collectors.toMap(BlockValidator::supports, v -> v));
        this.semanticValidators = semanticValidators;
    }

    public void validate(LoadedCourse loaded) {
        List<ValidationError> errors = new ArrayList<>();
        validateCourse(loaded, errors);
        for (SemanticValidator sv : semanticValidators) {
            sv.validate(loaded, errors);
        }
        if (!errors.isEmpty()) {
            throw new ContentValidationException(errors);
        }
    }

    private void validateCourse(LoadedCourse loaded, List<ValidationError> errors) {
        CourseMetadataDto meta = loaded.course().course();
        CourseVersionDto version = loaded.course().version();

        requireNotBlank(meta.code(), COURSE, "code is required", errors);
        requireNotBlank(meta.name(), COURSE, "name is required", errors);
        requireNotBlank(version.version(), COURSE, "version is required", errors);

        List<TopicYaml> topics = loaded.topics();
        if (topics == null || topics.isEmpty()) {
            errors.add(new ValidationError(COURSE, "has no topics"));
        } else {
            validateTopics(topics, errors);
        }
    }

    private void validateTopics(List<TopicYaml> topics, List<ValidationError> errors) {
        checkDuplicates(
                topics.stream().map(t -> t.topic().code()).toList(),
                COURSE,
                "duplicate topic code",
                errors);
        for (TopicYaml topicYaml : topics) {
            validateTopic(topicYaml, errors);
        }
    }

    private void validateTopic(TopicYaml topicYaml, List<ValidationError> errors) {
        TopicDto topic = topicYaml.topic();
        String topicLocation = "Topic " + codeOrUnknown(topic.code());

        requireNotBlank(topic.code(), topicLocation, "code is required", errors);
        requireNotBlank(topic.name(), topicLocation, "name is required", errors);

        List<LessonDto> lessons = topicYaml.lessons();
        if (lessons == null || lessons.isEmpty()) {
            errors.add(new ValidationError(topicLocation, "has no lessons"));
        } else {
            validateLessons(lessons, topic.code(), topicLocation, errors);
        }
    }

    private void validateLessons(
            List<LessonDto> lessons,
            String topicCode,
            String topicLocation,
            List<ValidationError> errors) {
        checkDuplicates(
                lessons.stream().map(LessonDto::code).toList(),
                topicLocation,
                "duplicate lesson code",
                errors);
        for (LessonDto lesson : lessons) {
            validateLesson(lesson, topicCode, topicLocation, errors);
        }
    }

    private void validateLesson(
            LessonDto lesson,
            String topicCode,
            String topicLocation,
            List<ValidationError> errors) {
        String location = topicLocation + " > Lesson " + codeOrUnknown(lesson.code());

        requireNotBlank(lesson.code(), location, "code is required", errors);
        requireNotBlank(lesson.name(), location, "name is required", errors);

        List<LessonBlockDto> blocks = lesson.blocks();
        if (blocks == null || blocks.isEmpty()) {
            errors.add(new ValidationError(location, "has no blocks"));
        } else {
            validateBlocks(blocks, topicCode, lesson.code(), errors);
        }
    }

    private void validateBlocks(
            List<LessonBlockDto> blocks,
            String topicCode,
            String lessonCode,
            List<ValidationError> errors) {
        for (int i = 0; i < blocks.size(); i++) {
            validateBlock(
                    blocks.get(i), new ValidationContext(topicCode, lessonCode, i + 1), errors);
        }
    }

    private void validateBlock(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        String location = ctx.location();
        if (block.type() == null) {
            errors.add(new ValidationError(location, "type is required"));
        }
        if (block.config() == null) {
            errors.add(new ValidationError(location, "config is required"));
        }
        if (block.xp() != null && block.xp() < 0) {
            errors.add(
                    new ValidationError(
                            location, "xpReward must be >= 0 (got " + block.xp() + ")"));
        }
        if (block.type() != null && block.config() != null) {
            BlockValidator validator = blockValidators.get(block.type());
            if (validator == null) {
                errors.add(
                        new ValidationError(
                                location,
                                "no validator registered for block type " + block.type()));
            } else {
                validator.validate(block, ctx, errors);
            }
        }
    }

    private void requireNotBlank(
            String value, String location, String message, List<ValidationError> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new ValidationError(location, message));
        }
    }

    private void checkDuplicates(
            List<String> codes, String location, String message, List<ValidationError> errors) {
        Set<String> seen = new HashSet<>();
        for (String code : codes) {
            if (code != null && !code.isBlank() && !seen.add(code)) {
                errors.add(new ValidationError(location, message + ": " + code));
            }
        }
    }

    private String codeOrUnknown(String code) {
        return code != null && !code.isBlank() ? code : UNKNOWN;
    }
}
