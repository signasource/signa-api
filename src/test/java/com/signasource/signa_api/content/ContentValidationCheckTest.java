package com.signasource.signa_api.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.signasource.signa_api.content.service.ContentLoader;
import com.signasource.signa_api.content.util.BlockConfigParser;
import com.signasource.signa_api.content.util.YamlParser;
import com.signasource.signa_api.content.validator.ContentValidator;
import com.signasource.signa_api.content.validator.block.BlockValidator;
import com.signasource.signa_api.content.validator.block.ContextResponseValidator;
import com.signasource.signa_api.content.validator.block.InfoValidator;
import com.signasource.signa_api.content.validator.block.MatchValidator;
import com.signasource.signa_api.content.validator.block.SelectMeaningValidator;
import com.signasource.signa_api.content.validator.block.SelectSignValidator;
import com.signasource.signa_api.content.validator.block.VisualRecognitionValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class ContentValidationCheckTest {

    private static final Path CONTENT_ROOT = Path.of("src/main/resources/content");

    private final ContentLoader loader;
    private final ContentValidator validator;

    ContentValidationCheckTest() {
        ObjectMapper mapper =
                new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        YamlParser yamlParser = new YamlParser(mapper);
        this.loader = new ContentLoader(new PathMatchingResourcePatternResolver(), yamlParser);

        BlockConfigParser configParser = new BlockConfigParser(mapper);
        List<BlockValidator> blockValidators =
                List.of(
                        new InfoValidator(configParser),
                        new SelectMeaningValidator(configParser),
                        new SelectSignValidator(configParser),
                        new MatchValidator(configParser),
                        new VisualRecognitionValidator(configParser),
                        new ContextResponseValidator(configParser));
        this.validator = new ContentValidator(blockValidators);
    }

    @TestFactory
    Stream<DynamicTest> everyCourseIsValid() throws IOException {
        List<Course> courses = discoverCourses();
        assertThat(courses).as("courses discovered under %s", CONTENT_ROOT).isNotEmpty();
        return courses.stream()
                .map(
                        course ->
                                DynamicTest.dynamicTest(
                                        course.signLanguageCode() + "/" + course.courseCode(),
                                        () ->
                                                assertThatNoException()
                                                        .isThrownBy(
                                                                () ->
                                                                        validator.validate(
                                                                                loader.load(
                                                                                        course
                                                                                                .signLanguageCode(),
                                                                                        course
                                                                                                .courseCode())))));
    }

    private List<Course> discoverCourses() throws IOException {
        List<Course> courses = new ArrayList<>();
        try (Stream<Path> languages = Files.list(CONTENT_ROOT)) {
            for (Path language : languages.filter(Files::isDirectory).toList()) {
                try (Stream<Path> courseDirs = Files.list(language)) {
                    courseDirs
                            .filter(Files::isDirectory)
                            .filter(dir -> Files.exists(dir.resolve("course.yml")))
                            .forEach(
                                    dir ->
                                            courses.add(
                                                    new Course(
                                                            language.getFileName().toString(),
                                                            dir.getFileName().toString())));
                }
            }
        }
        return courses;
    }

    private record Course(String signLanguageCode, String courseCode) {}
}
