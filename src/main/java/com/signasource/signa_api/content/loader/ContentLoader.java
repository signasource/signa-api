package com.signasource.signa_api.content.loader;

import com.signasource.signa_api.content.dto.CourseYaml;
import com.signasource.signa_api.content.dto.TopicYaml;
import com.signasource.signa_api.content.exception.ContentLoadException;
import com.signasource.signa_api.content.exception.CourseFileNotFoundException;
import com.signasource.signa_api.content.exception.TopicFileNotFoundException;
import com.signasource.signa_api.content.parser.YamlParser;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class ContentLoader {

    private static final String CONTENT_BASE = "classpath:content/";

    private final ResourceLoader resourceLoader;
    private final YamlParser yamlParser;

    public ContentLoader(ResourceLoader resourceLoader, YamlParser yamlParser) {
        this.resourceLoader = resourceLoader;
        this.yamlParser = yamlParser;
    }

    public LoadedCourse load(String signLanguageCode, String courseCode) {
        String courseBasePath = CONTENT_BASE + signLanguageCode + "/" + courseCode + "/";

        Resource courseFile = resourceLoader.getResource(courseBasePath + "course.yml");
        if (!courseFile.exists()) {
            throw new CourseFileNotFoundException(courseBasePath + "course.yml");
        }

        CourseYaml courseYaml = yamlParser.parse(courseFile, CourseYaml.class);
        if (courseYaml.topics() == null) {
            throw new ContentLoadException(
                    "course.yml at " + courseBasePath + " has no 'topics' section");
        }
        List<TopicYaml> topics = loadTopics(courseYaml.topics(), courseBasePath);

        return new LoadedCourse(signLanguageCode, courseYaml, topics);
    }

    private List<TopicYaml> loadTopics(List<String> topicFiles, String courseBasePath) {
        return topicFiles.stream().map(topicFile -> loadTopic(topicFile, courseBasePath)).toList();
    }

    private TopicYaml loadTopic(String topicFile, String courseBasePath) {
        Resource resource = resourceLoader.getResource(courseBasePath + topicFile);
        if (!resource.exists()) {
            throw new TopicFileNotFoundException(topicFile, courseBasePath);
        }
        return yamlParser.parse(resource, TopicYaml.class);
    }
}
