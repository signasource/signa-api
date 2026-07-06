package com.signasource.signa_api.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.content.dto.load.CourseRef;
import com.signasource.signa_api.content.dto.load.LoadedCourse;
import com.signasource.signa_api.content.exception.ContentLoadException;
import com.signasource.signa_api.content.exception.ContentParseException;
import com.signasource.signa_api.content.exception.CourseFileNotFoundException;
import com.signasource.signa_api.content.exception.TopicFileNotFoundException;
import com.signasource.signa_api.content.util.YamlParser;
import com.signasource.signa_api.learning.entity.VersionStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class ContentLoaderTest {

    private static final String SIGN_LANG = "TSLANG";
    private static final String HAPPY_COURSE = "happy-course";

    private ContentLoader loader;

    @BeforeEach
    void setUp() {
        loader =
                new ContentLoader(
                        new PathMatchingResourcePatternResolver(),
                        new YamlParser(new ObjectMapper()));
    }

    @Test
    void shouldDiscoverEveryCourseOnClasspath() {
        List<CourseRef> refs = loader.discover();

        assertThat(refs)
                .contains(
                        new CourseRef("LSA", "basic-course"),
                        new CourseRef(SIGN_LANG, HAPPY_COURSE));
    }

    @Test
    void shouldReturnDiscoveredCoursesSortedAndDistinct() {
        List<CourseRef> refs = loader.discover();

        assertThat(refs)
                .doesNotHaveDuplicates()
                .isSortedAccordingTo(
                        (a, b) -> {
                            int byLang = a.signLanguageCode().compareTo(b.signLanguageCode());
                            return byLang != 0 ? byLang : a.courseCode().compareTo(b.courseCode());
                        });
    }

    @Test
    void shouldLoadCourseSuccessfully() {
        LoadedCourse result = loader.load(SIGN_LANG, HAPPY_COURSE);

        assertThat(result.signLanguageCode()).isEqualTo(SIGN_LANG);
        assertThat(result.course().course().code()).isEqualTo(HAPPY_COURSE);
        assertThat(result.course().course().name()).isEqualTo("Test Course");
        assertThat(result.course().version().status()).isEqualTo(VersionStatus.DRAFT);
    }

    @Test
    void shouldLoadAllTopicsListedInCourseYml() {
        LoadedCourse result = loader.load(SIGN_LANG, HAPPY_COURSE);

        assertThat(result.topics()).hasSize(2);
        assertThat(result.topics().get(0).topic().code()).isEqualTo("topic-1");
        assertThat(result.topics().get(1).topic().code()).isEqualTo("topic-2");
    }

    @Test
    void shouldLoadLessonsAndBlocksWithinEachTopic() {
        LoadedCourse result = loader.load(SIGN_LANG, HAPPY_COURSE);

        assertThat(result.topics().get(0).lessons()).hasSize(1);
        assertThat(result.topics().get(0).lessons().get(0).blocks()).hasSize(2);
    }

    @Test
    void shouldThrowWhenCourseYmlIsMissing() {
        assertThatThrownBy(() -> loader.load(SIGN_LANG, "nonexistent-course"))
                .isInstanceOf(CourseFileNotFoundException.class)
                .hasMessageContaining("course.yml");
    }

    @Test
    void shouldThrowWhenTopicFileIsMissing() {
        assertThatThrownBy(() -> loader.load(SIGN_LANG, "missing-topic-course"))
                .isInstanceOf(TopicFileNotFoundException.class)
                .hasMessageContaining("nonexistent-topic.yml");
    }

    @Test
    void shouldThrowWhenTopicYamlIsInvalid() {
        assertThatThrownBy(() -> loader.load(SIGN_LANG, "bad-yaml-course"))
                .isInstanceOf(ContentParseException.class);
    }

    @Test
    void shouldThrowWhenCourseYmlHasNoTopicsSection() {
        assertThatThrownBy(() -> loader.load(SIGN_LANG, "no-topics-course"))
                .isInstanceOf(ContentLoadException.class)
                .hasMessageContaining("no 'topics' section");
    }
}
