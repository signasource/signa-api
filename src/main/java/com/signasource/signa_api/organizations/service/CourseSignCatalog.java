package com.signasource.signa_api.organizations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.organizations.dto.CatalogModuleResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The signs a purchasable course teaches, grouped by module, so an organization can check them
 * before buying. A sign introduced twice is listed once, in the first module that teaches it; the
 * number of listed signs is what the price is based on.
 */
@Component
@RequiredArgsConstructor
public class CourseSignCatalog {

    private final LessonBlockRepository lessonBlockRepository;
    private final ObjectMapper objectMapper;

    public List<CatalogModuleResponse> modules(UUID versionId) {
        Map<UUID, String> titles = new LinkedHashMap<>();
        Map<UUID, List<String>> signsByTopic = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (LessonBlock block : lessonBlockRepository.findIntroducedSigns(versionId)) {
            Topic topic = block.getLesson().getTopic();
            titles.putIfAbsent(topic.getId(), topic.getTitle());
            List<String> signs =
                    signsByTopic.computeIfAbsent(topic.getId(), id -> new ArrayList<>());
            word(block).filter(word -> seen.add(word.toLowerCase())).ifPresent(signs::add);
        }
        return titles.entrySet().stream()
                .filter(entry -> !signsByTopic.get(entry.getKey()).isEmpty())
                .map(
                        entry ->
                                new CatalogModuleResponse(
                                        entry.getValue(),
                                        List.copyOf(signsByTopic.get(entry.getKey()))))
                .toList();
    }

    public static int signCount(List<CatalogModuleResponse> modules) {
        return modules.stream().mapToInt(module -> module.signs().size()).sum();
    }

    /** The word shown to learners; the catalog {@code meaning} when the block has none. */
    private Optional<String> word(LessonBlock block) {
        try {
            JsonNode config = objectMapper.readTree(block.getConfig());
            return text(config, "word").or(() -> text(config, "meaning"));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> text(JsonNode config, String field) {
        JsonNode node = config.path(field);
        if (!node.isTextual()) {
            return Optional.empty();
        }
        String text = node.asText().strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }
}
