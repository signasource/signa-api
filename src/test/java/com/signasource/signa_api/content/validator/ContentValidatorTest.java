package com.signasource.signa_api.content.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.signasource.signa_api.content.dto.load.LoadedCourse;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.CourseMetadataDto;
import com.signasource.signa_api.content.dto.yaml.CourseVersionDto;
import com.signasource.signa_api.content.dto.yaml.CourseYaml;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.dto.yaml.LessonDto;
import com.signasource.signa_api.content.dto.yaml.TopicDto;
import com.signasource.signa_api.content.dto.yaml.TopicYaml;
import com.signasource.signa_api.content.exception.ContentValidationException;
import com.signasource.signa_api.content.util.BlockConfigParser;
import com.signasource.signa_api.content.validator.block.InfoValidator;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.VersionStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentValidatorTest {

    private ContentValidator validator;

    @BeforeEach
    void setUp() {
        BlockConfigParser parser = new BlockConfigParser(new ObjectMapper());
        validator = new ContentValidator(List.of(new InfoValidator(parser)));
    }

    // --- Valid cases ---

    @Test
    void shouldPassForValidCourse() {
        assertThatNoException().isThrownBy(() -> validator.validate(validCourse()));
    }

    @Test
    void shouldPassWithMultipleTopics() {
        assertThatNoException()
                .isThrownBy(
                        () ->
                                validator.validate(
                                        courseWith(List.of(validTopic("t1"), validTopic("t2")))));
    }

    @Test
    void shouldPassWithMultipleLessons() {
        TopicYaml topic = topicWith("t1", List.of(validLesson("l1"), validLesson("l2")));
        assertThatNoException().isThrownBy(() -> validator.validate(courseWith(List.of(topic))));
    }

    // --- Course validation ---

    @Test
    void shouldFailWhenCourseCodeIsBlank() {
        assertValidationFails(
                courseWithMeta(new CourseMetadataDto("", "Name", null, false, null)),
                "Course: code is required");
    }

    @Test
    void shouldFailWhenCourseNameIsBlank() {
        assertValidationFails(
                courseWithMeta(new CourseMetadataDto("code", null, null, false, null)),
                "Course: name is required");
    }

    @Test
    void shouldFailWhenVersionIsBlank() {
        assertValidationFails(
                courseWithVersion(new CourseVersionDto("", VersionStatus.DRAFT)),
                "Course: version is required");
    }

    @Test
    void shouldFailWhenTopicsListIsEmpty() {
        assertValidationFails(courseWith(List.of()), "Course: has no topics");
    }

    // --- Topic validation ---

    @Test
    void shouldFailWhenTopicCodeIsBlank() {
        TopicYaml topic =
                new TopicYaml(new TopicDto(null, "Name", null, null), List.of(validLesson("l1")));
        assertValidationFails(courseWith(List.of(topic)), "Topic (unknown): code is required");
    }

    @Test
    void shouldFailWhenTopicNameIsBlank() {
        TopicYaml topic =
                new TopicYaml(new TopicDto("t1", null, null, null), List.of(validLesson("l1")));
        assertValidationFails(courseWith(List.of(topic)), "Topic t1: name is required");
    }

    @Test
    void shouldFailWhenTopicHasNoLessons() {
        TopicYaml topic = new TopicYaml(new TopicDto("t1", "Name", null, null), List.of());
        assertValidationFails(courseWith(List.of(topic)), "Topic t1: has no lessons");
    }

    @Test
    void shouldFailWhenTopicCodesAreDuplicated() {
        assertValidationFails(
                courseWith(List.of(validTopic("t1"), validTopic("t1"))),
                "Course: duplicate topic code: t1");
    }

    // --- Lesson validation ---

    @Test
    void shouldFailWhenLessonCodeIsBlank() {
        LessonDto lesson = new LessonDto(null, "Name", null, List.of(validBlock()));
        assertValidationFails(
                courseWith(List.of(topicWith("t1", List.of(lesson)))),
                "Topic t1 > Lesson (unknown): code is required");
    }

    @Test
    void shouldFailWhenLessonNameIsBlank() {
        LessonDto lesson = new LessonDto("l1", null, null, List.of(validBlock()));
        assertValidationFails(
                courseWith(List.of(topicWith("t1", List.of(lesson)))),
                "Topic t1 > Lesson l1: name is required");
    }

    @Test
    void shouldFailWhenLessonHasNoBlocks() {
        LessonDto lesson = new LessonDto("l1", "Name", null, List.of());
        assertValidationFails(
                courseWith(List.of(topicWith("t1", List.of(lesson)))),
                "Topic t1 > Lesson l1: has no blocks");
    }

    @Test
    void shouldFailWhenLessonCodesAreDuplicatedWithinTopic() {
        assertValidationFails(
                courseWith(List.of(topicWith("t1", List.of(validLesson("l1"), validLesson("l1"))))),
                "Topic t1: duplicate lesson code: l1");
    }

    // --- Block validation ---

    @Test
    void shouldFailWhenBlockTypeIsNull() {
        LessonBlockDto block =
                new LessonBlockDto(null, null, JsonNodeFactory.instance.objectNode());
        assertValidationFails(
                courseWith(List.of(topicWith("t1", List.of(lessonWith("l1", List.of(block)))))),
                "Topic t1 > Lesson l1 > Block #1: type is required");
    }

    @Test
    void shouldFailWhenBlockConfigIsNull() {
        LessonBlockDto block = new LessonBlockDto(BlockType.INFO, null, null);
        assertValidationFails(
                courseWith(List.of(topicWith("t1", List.of(lessonWith("l1", List.of(block)))))),
                "Topic t1 > Lesson l1 > Block #1: config is required");
    }

    @Test
    void shouldFailWhenXpRewardIsNegative() {
        LessonBlockDto block = new LessonBlockDto(BlockType.INFO, -1, infoConfig("Hello"));
        assertValidationFails(
                courseWith(List.of(topicWith("t1", List.of(lessonWith("l1", List.of(block)))))),
                "Topic t1 > Lesson l1 > Block #1: xpReward must be >= 0 (got -1)");
    }

    @Test
    void shouldFailWhenNoValidatorRegisteredForBlockType() {
        LessonBlockDto block =
                new LessonBlockDto(BlockType.CONTEXT, null, JsonNodeFactory.instance.objectNode());
        assertValidationFails(
                courseWith(List.of(topicWith("t1", List.of(lessonWith("l1", List.of(block)))))),
                "Topic t1 > Lesson l1 > Block #1: no validator registered for block type CONTEXT");
    }

    // --- Multiple errors ---

    @Test
    void shouldAccumulateAllErrorsBeforeThrowing() {
        var meta = new CourseMetadataDto("", "", null, false, null);
        var version = new CourseVersionDto("", VersionStatus.DRAFT);
        var yaml = new CourseYaml(meta, version, List.of());
        var course = new LoadedCourse("LSA", yaml, List.of());

        assertThatThrownBy(() -> validator.validate(course))
                .isInstanceOf(ContentValidationException.class)
                .satisfies(
                        ex -> {
                            List<ValidationError> errors =
                                    ((ContentValidationException) ex).errors();
                            assertThat(errors)
                                    .extracting(ValidationError::render)
                                    .containsExactlyInAnyOrder(
                                            "Course: code is required",
                                            "Course: name is required",
                                            "Course: version is required",
                                            "Course: has no topics");
                        });
    }

    // --- Helpers ---

    private LoadedCourse validCourse() {
        return courseWith(List.of(validTopic("topic-1")));
    }

    private LoadedCourse courseWith(List<TopicYaml> topics) {
        return new LoadedCourse("LSA", validCourseYaml(), topics);
    }

    private LoadedCourse courseWithMeta(CourseMetadataDto meta) {
        var yaml = new CourseYaml(meta, validVersion(), List.of("topic-01.yml"));
        return new LoadedCourse("LSA", yaml, List.of(validTopic("topic-1")));
    }

    private LoadedCourse courseWithVersion(CourseVersionDto version) {
        var yaml = new CourseYaml(validMeta(), version, List.of("topic-01.yml"));
        return new LoadedCourse("LSA", yaml, List.of(validTopic("topic-1")));
    }

    private CourseYaml validCourseYaml() {
        return new CourseYaml(validMeta(), validVersion(), List.of("topic-01.yml"));
    }

    private CourseMetadataDto validMeta() {
        return new CourseMetadataDto("course-01", "My Course", "Desc", false, null);
    }

    private CourseVersionDto validVersion() {
        return new CourseVersionDto("1.0.0", VersionStatus.DRAFT);
    }

    private TopicYaml validTopic(String code) {
        return new TopicYaml(
                new TopicDto(code, "Topic Name", null, null), List.of(validLesson("lesson-1")));
    }

    private TopicYaml topicWith(String code, List<LessonDto> lessons) {
        return new TopicYaml(new TopicDto(code, "Topic Name", null, null), lessons);
    }

    private LessonDto validLesson(String code) {
        return new LessonDto(code, "Lesson Name", null, List.of(validBlock()));
    }

    private LessonDto lessonWith(String code, List<LessonBlockDto> blocks) {
        return new LessonDto(code, "Lesson Name", null, blocks);
    }

    private LessonBlockDto validBlock() {
        return new LessonBlockDto(BlockType.INFO, null, infoConfig("Hello"));
    }

    private ObjectNode infoConfig(String text) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("text", text);
        return node;
    }

    private void assertValidationFails(LoadedCourse course, String... expectedErrors) {
        assertThatThrownBy(() -> validator.validate(course))
                .isInstanceOf(ContentValidationException.class)
                .satisfies(
                        ex ->
                                assertThat(((ContentValidationException) ex).errors())
                                        .extracting(ValidationError::render)
                                        .contains(expectedErrors));
    }
}
