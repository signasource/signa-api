package com.signasource.signa_api.content.service;

import com.signasource.signa_api.content.dto.load.CourseRef;
import com.signasource.signa_api.content.dto.load.LoadedCourse;
import com.signasource.signa_api.content.dto.yaml.CourseYaml;
import com.signasource.signa_api.content.dto.yaml.TopicYaml;
import com.signasource.signa_api.content.exception.ContentLoadException;
import com.signasource.signa_api.content.exception.CourseFileNotFoundException;
import com.signasource.signa_api.content.exception.TopicFileNotFoundException;
import com.signasource.signa_api.content.util.YamlParser;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class ContentLoader {

    private static final String CONTENT_BASE = "classpath:content/";
    private static final String COURSE_FILE = "course.yml";
    private static final String COURSE_FILES = "classpath*:content/*/*/course.yml";
    private static final Pattern PATH = Pattern.compile("content/([^/]+)/([^/]+)/course\\.yml");

    private final ResourcePatternResolver resolver;
    private final YamlParser yamlParser;

    public ContentLoader(ResourcePatternResolver resolver, YamlParser yamlParser) {
        this.resolver = resolver;
        this.yamlParser = yamlParser;
    }

    /** Scans the classpath and returns every course found, sorted by language then code. */
    public List<CourseRef> discover() {
        try {
            Resource[] resources = resolver.getResources(COURSE_FILES);
            return Arrays.stream(resources)
                    .map(this::toRef)
                    .flatMap(Optional::stream)
                    .distinct()
                    .sorted(
                            Comparator.comparing(CourseRef::signLanguageCode)
                                    .thenComparing(CourseRef::courseCode))
                    .toList();
        } catch (IOException e) {
            throw new ContentLoadException("Failed to scan content under " + COURSE_FILES, e);
        }
    }

    /** Reads and parses a single course ({@code course.yml} plus its topic files) into DTOs. */
    public LoadedCourse load(String signLanguageCode, String courseCode) {
        String courseBasePath = CONTENT_BASE + signLanguageCode + "/" + courseCode + "/";

        Resource courseFile = resolver.getResource(courseBasePath + COURSE_FILE);
        if (!courseFile.exists()) {
            throw new CourseFileNotFoundException(courseBasePath + COURSE_FILE);
        }

        CourseYaml courseYaml = yamlParser.parse(courseFile, CourseYaml.class);
        if (courseYaml.topics() == null) {
            throw new ContentLoadException(
                    "course.yml at " + courseBasePath + " has no 'topics' section");
        }
        List<TopicYaml> topics = loadTopics(courseYaml.topics(), courseBasePath);

        return new LoadedCourse(signLanguageCode, courseYaml, topics);
    }

    private Optional<CourseRef> toRef(Resource resource) {
        try {
            Matcher matcher = PATH.matcher(resource.getURL().toString());
            return matcher.find()
                    ? Optional.of(new CourseRef(matcher.group(1), matcher.group(2)))
                    : Optional.empty();
        } catch (IOException e) {
            throw new ContentLoadException("Failed to read content resource URL", e);
        }
    }

    private List<TopicYaml> loadTopics(List<String> topicFiles, String courseBasePath) {
        return topicFiles.stream().map(topicFile -> loadTopic(topicFile, courseBasePath)).toList();
    }

    private TopicYaml loadTopic(String topicFile, String courseBasePath) {
        Resource resource = resolver.getResource(courseBasePath + topicFile);
        if (!resource.exists()) {
            throw new TopicFileNotFoundException(topicFile, courseBasePath);
        }
        return yamlParser.parse(resource, TopicYaml.class);
    }
}
