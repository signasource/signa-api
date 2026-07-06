package com.signasource.signa_api.content.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.signasource.signa_api.content.exception.ContentParseException;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class YamlParser {

    private final ObjectMapper mapper;

    /**
     * Derives the YAML mapper from the application's shared {@link ObjectMapper} so both YAML
     * parsing and block-config binding ({@code BlockConfigParser}) share the same configuration
     * (e.g. the snake_case naming strategy), avoiding silent divergence. Unknown properties fail
     * the parse on purpose: content is treated like source code, so a typo'd or stray key is a hard
     * error rather than a silently-dropped field.
     */
    public YamlParser(ObjectMapper objectMapper) {
        this.mapper =
                objectMapper
                        .copyWith(new YAMLFactory())
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public <T> T parse(Resource resource, Class<T> clazz) {
        try (InputStream in = resource.getInputStream()) {
            return mapper.readValue(in, clazz);
        } catch (IOException e) {
            throw new ContentParseException(resource.getDescription(), clazz, e);
        }
    }
}
