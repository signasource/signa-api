package com.signasource.signa_api.content.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.signasource.signa_api.content.dto.CourseYaml;
import com.signasource.signa_api.content.dto.TopicYaml;
import com.signasource.signa_api.content.exception.ContentParseException;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.VersionStatus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class YamlParserTest {

    private YamlParser parser;

    @BeforeEach
    void setUp() {
        parser = new YamlParser();
    }

    @Test
    void shouldParseCourseYaml() {
        String yaml =
                """
                course:
                  code: test-course
                  name: Test
                  description: A description.
                  free: true
                  cover: img.png
                version:
                  version: "1.0.0"
                  status: DRAFT
                topics:
                  - topic-01.yml
                  - topic-02.yml
                """;

        CourseYaml result = parser.parse(resource(yaml), CourseYaml.class);

        assertThat(result.course().code()).isEqualTo("test-course");
        assertThat(result.course().name()).isEqualTo("Test");
        assertThat(result.course().free()).isTrue();
        assertThat(result.version().version()).isEqualTo("1.0.0");
        assertThat(result.version().status()).isEqualTo(VersionStatus.DRAFT);
        assertThat(result.topics()).containsExactly("topic-01.yml", "topic-02.yml");
    }

    @Test
    void shouldParseTopicYaml() {
        String yaml =
                """
                topic:
                  code: topic-1
                  name: Topic One
                  description: Desc.
                lessons:
                  - code: lesson-1
                    name: Lesson One
                    description: Lesson desc.
                    blocks:
                      - type: INFO
                        config:
                          text: Hello
                      - type: SELECT_MEANING
                        xp: 10
                        config:
                          sign: hola
                """;

        TopicYaml result = parser.parse(resource(yaml), TopicYaml.class);

        assertThat(result.topic().code()).isEqualTo("topic-1");
        assertThat(result.lessons()).hasSize(1);
        assertThat(result.lessons().get(0).blocks()).hasSize(2);
        assertThat(result.lessons().get(0).blocks().get(0).type()).isEqualTo(BlockType.INFO);
        assertThat(result.lessons().get(0).blocks().get(0).xp()).isNull();
        assertThat(result.lessons().get(0).blocks().get(1).xp()).isEqualTo(10);
    }

    @Test
    void shouldThrowContentParseExceptionOnInvalidYaml() {
        Resource invalid = resource("{{invalid yaml: [unclosed");

        assertThatThrownBy(() -> parser.parse(invalid, CourseYaml.class))
                .isInstanceOf(ContentParseException.class);
    }

    private Resource resource(String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
