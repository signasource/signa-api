package com.signasource.signa_api.content.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BlockConfigParser {

    private final ObjectMapper mapper;

    public BlockConfigParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public <T> Optional<T> parse(JsonNode node, Class<T> clazz) {
        try {
            return Optional.of(mapper.treeToValue(node, clazz));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
