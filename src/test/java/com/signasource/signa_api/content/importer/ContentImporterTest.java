package com.signasource.signa_api.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.content.dto.CourseMetadataDto;
import com.signasource.signa_api.content.dto.CourseVersionDto;
import com.signasource.signa_api.content.dto.CourseYaml;
import com.signasource.signa_api.content.dto.LessonBlockDto;
import com.signasource.signa_api.content.dto.LessonDto;
import com.signasource.signa_api.content.dto.TopicDto;
import com.signasource.signa_api.content.dto.TopicYaml;
import com.signasource.signa_api.content.exception.CourseAlreadyExistsException;
import com.signasource.signa_api.content.exception.SignLanguageNotFoundException;
import com.signasource.signa_api.content.loader.ContentLoader;
import com.signasource.signa_api.content.loader.LoadedCourse;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ContentImporterTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private ContentImporter contentImporter;
    @Autowired private ContentLoader contentLoader;
    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;

    private LoadedCourse happyCourse;

    @BeforeEach
    void setUp() {
        signLanguageRepository.save(
                SignLanguage.builder()
                        .code("TSLANG")
                        .name("Test Sign Language")
                        .countryCode("TST")
                        .build());
        happyCourse = contentLoader.load("TSLANG", "happy-course");
    }

    @AfterEach
    void tearDown() {
        signLanguageRepository.deleteAll();
    }

    @Test
    void shouldImportCourseCompletely() {
        contentImporter.importCourse(happyCourse);

        assertThat(courseRepository.count()).isEqualTo(1);
        assertThat(topicRepository.count()).isEqualTo(2);
        assertThat(lessonRepository.count()).isEqualTo(2);
        assertThat(lessonBlockRepository.count()).isEqualTo(3);
    }

    @Test
    void shouldAssignOrderByListPosition() {
        contentImporter.importCourse(happyCourse);

        // Topics have globally unique orders within the course version
        var topics = topicRepository.findAll(Sort.by("order"));
        assertThat(topics.get(0).getCode()).isEqualTo("topic-1");
        assertThat(topics.get(0).getOrder()).isEqualTo(0);
        assertThat(topics.get(1).getCode()).isEqualTo("topic-2");
        assertThat(topics.get(1).getOrder()).isEqualTo(1);

        // Each lesson is first (and only) in its topic, so order=0 for both
        var allLessons = lessonRepository.findAll();
        var lesson1 =
                allLessons.stream()
                        .filter(l -> "lesson-1".equals(l.getCode()))
                        .findFirst()
                        .orElseThrow();
        var lesson2 =
                allLessons.stream()
                        .filter(l -> "lesson-2".equals(l.getCode()))
                        .findFirst()
                        .orElseThrow();
        assertThat(lesson1.getOrder()).isEqualTo(0);
        assertThat(lesson2.getOrder()).isEqualTo(0);

        // Blocks within lesson-1: INFO first, SELECT_MEANING second
        var allBlocks = lessonBlockRepository.findAll();
        var infoBlock =
                allBlocks.stream()
                        .filter(b -> b.getType() == BlockType.INFO)
                        .findFirst()
                        .orElseThrow();
        var selectBlock =
                allBlocks.stream()
                        .filter(b -> b.getType() == BlockType.SELECT_MEANING)
                        .findFirst()
                        .orElseThrow();
        assertThat(infoBlock.getOrder()).isEqualTo(0);
        assertThat(selectBlock.getOrder()).isEqualTo(1);
    }

    @Test
    void shouldPersistConfigAsJson() {
        contentImporter.importCourse(happyCourse);

        List<LessonBlock> blocks = lessonBlockRepository.findAll();
        LessonBlock infoBlock =
                blocks.stream()
                        .filter(b -> b.getType() == BlockType.INFO)
                        .findFirst()
                        .orElseThrow();

        assertThat(infoBlock.getConfig()).contains("text");
        assertThat(infoBlock.getConfig()).contains("Hello");
    }

    @Test
    void shouldMapNullXpAsNull() {
        contentImporter.importCourse(happyCourse);

        List<LessonBlock> blocks = lessonBlockRepository.findAll();
        LessonBlock infoBlock =
                blocks.stream()
                        .filter(b -> b.getType() == BlockType.INFO)
                        .findFirst()
                        .orElseThrow();

        assertThat(infoBlock.getXpReward()).isNull();
    }

    @Test
    void shouldMapExplicitXpValue() {
        contentImporter.importCourse(happyCourse);

        List<LessonBlock> blocks = lessonBlockRepository.findAll();
        LessonBlock selectBlock =
                blocks.stream()
                        .filter(b -> b.getType() == BlockType.SELECT_MEANING)
                        .findFirst()
                        .orElseThrow();

        assertThat(selectBlock.getXpReward()).isEqualTo(10);
    }

    @Test
    void shouldThrowWhenSignLanguageNotFound() {
        LoadedCourse unknown =
                new LoadedCourse("UNKNOWN", happyCourse.course(), happyCourse.topics());

        assertThatThrownBy(() -> contentImporter.importCourse(unknown))
                .isInstanceOf(SignLanguageNotFoundException.class)
                .hasMessageContaining("UNKNOWN");

        assertThat(courseRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldThrowWhenCourseAlreadyExists() {
        contentImporter.importCourse(happyCourse);

        assertThatThrownBy(() -> contentImporter.importCourse(happyCourse))
                .isInstanceOf(CourseAlreadyExistsException.class)
                .hasMessageContaining("happy-course");

        assertThat(courseRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRollbackWhenSaveFails() {
        // Build a LoadedCourse that passes the pre-save checks (sign language exists,
        // course code is new) but has a null block type, which Hibernate rejects
        // with a PropertyValueException during flush — triggering full rollback.
        LessonBlockDto badBlock =
                new LessonBlockDto(null, null, JsonNodeFactory.instance.objectNode());
        LessonDto lesson = new LessonDto("l1", "Lesson", null, List.of(badBlock));
        TopicYaml topic = new TopicYaml(new TopicDto("t1", "Topic", null, null), List.of(lesson));
        CourseYaml yaml =
                new CourseYaml(
                        new CourseMetadataDto(null, "Rollback Course", null, false, null),
                        new CourseVersionDto("1.0.0", VersionStatus.DRAFT),
                        List.of("t1.yml"));
        LoadedCourse loaded = new LoadedCourse("TSLANG", yaml, List.of(topic));

        assertThatThrownBy(() -> contentImporter.importCourse(loaded))
                .isInstanceOf(RuntimeException.class);

        assertThat(courseRepository.count()).isEqualTo(0);
        assertThat(lessonBlockRepository.count()).isEqualTo(0);
    }
}
