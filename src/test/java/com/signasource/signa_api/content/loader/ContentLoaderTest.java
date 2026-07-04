package com.signasource.signa_api.content.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.signasource.signa_api.content.exception.ContentParseException;
import com.signasource.signa_api.content.exception.CourseFileNotFoundException;
import com.signasource.signa_api.content.exception.TopicFileNotFoundException;
import com.signasource.signa_api.content.parser.YamlParser;
import com.signasource.signa_api.learning.entity.VersionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class ContentLoaderTest {

    private ContentLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ContentLoader(new DefaultResourceLoader(), new YamlParser());
    }

    @Test
    void shouldLoadCourseSuccessfully() {
        LoadedCourse result = loader.load("TSLANG", "happy-course");

        assertThat(result.signLanguageCode()).isEqualTo("TSLANG");
        assertThat(result.coursePath()).isEqualTo("TSLANG/happy-course");
        assertThat(result.course().course().name()).isEqualTo("Test Course");
        assertThat(result.course().version().status()).isEqualTo(VersionStatus.DRAFT);
    }

    @Test
    void shouldLoadAllTopicsListedInCourseYml() {
        LoadedCourse result = loader.load("TSLANG", "happy-course");

        assertThat(result.topics()).hasSize(2);
        assertThat(result.topics().get(0).topic().code()).isEqualTo("topic-1");
        assertThat(result.topics().get(1).topic().code()).isEqualTo("topic-2");
    }

    @Test
    void shouldLoadLessonsAndBlocksWithinEachTopic() {
        LoadedCourse result = loader.load("TSLANG", "happy-course");

        assertThat(result.topics().get(0).lessons()).hasSize(1);
        assertThat(result.topics().get(0).lessons().get(0).blocks()).hasSize(2);
    }

    @Test
    void shouldThrowWhenCourseYmlIsMissing() {
        assertThatThrownBy(() -> loader.load("TSLANG", "nonexistent-course"))
                .isInstanceOf(CourseFileNotFoundException.class)
                .hasMessageContaining("course.yml");
    }

    @Test
    void shouldThrowWhenTopicFileIsMissing() {
        assertThatThrownBy(() -> loader.load("TSLANG", "missing-topic-course"))
                .isInstanceOf(TopicFileNotFoundException.class)
                .hasMessageContaining("nonexistent-topic.yml");
    }

    @Test
    void shouldThrowWhenTopicYamlIsInvalid() {
        assertThatThrownBy(() -> loader.load("TSLANG", "bad-yaml-course"))
                .isInstanceOf(ContentParseException.class);
    }
}
