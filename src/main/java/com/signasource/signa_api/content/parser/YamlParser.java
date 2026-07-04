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

    private final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public <T> T parse(Resource resource, Class<T> clazz) {
        try (InputStream in = resource.getInputStream()) {
            return mapper.readValue(in, clazz);
        } catch (IOException e) {
            throw new ContentParseException(resource.getDescription(), clazz, e);
        }
    }
}
