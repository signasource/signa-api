package com.signasource.signa_api.learning.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.LessonBlock;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Where a catalog sign is referenced inside lesson block configs.
 *
 * <p>Blocks point at signs by meaning (plain text), not by id, and the same meaning also appears as
 * an answer option ({@code SELECT_MEANING.options}, {@code CONTEXT_RESPONSE.answer}...). So a
 * reference is any string value in the config that equals the meaning, ignoring case: that is what
 * the lesson player matches, and what a rename has to rewrite for the lesson to keep working.
 *
 * <p>{@code PERFORM_SIGN}, {@code INVISIBLE_SIGNS} and {@code SPELL_NAME} name recognition-model
 * labels, not catalog signs (see SignCatalogExtractor), and {@code INFO} is free text: they are
 * never touched.
 */
@Component
@RequiredArgsConstructor
public class SignReferences {

    public static final Set<BlockType> CATALOG_BLOCK_TYPES =
            EnumSet.of(
                    BlockType.INTRODUCE_SIGN,
                    BlockType.SELECT_MEANING,
                    BlockType.SELECT_SIGN,
                    BlockType.CONTEXT_RESPONSE,
                    BlockType.MATCH,
                    BlockType.VISUAL_RECOGNITION);

    private final ObjectMapper objectMapper;

    /** Every string value in the block's config, stripped and lower-cased. Empty if unparsable. */
    public Set<String> values(LessonBlock block) {
        Set<String> out = new HashSet<>();
        if (!CATALOG_BLOCK_TYPES.contains(block.getType())) {
            return out;
        }
        JsonNode root = parse(block.getConfig());
        if (root != null) {
            collect(root, out);
        }
        return out;
    }

    public boolean references(LessonBlock block, String meaning) {
        return values(block).contains(normalize(meaning));
    }

    /**
     * The block's config with every value equal to {@code from} (ignoring case) replaced by {@code
     * to}, or {@code null} when nothing changed.
     */
    public String rename(LessonBlock block, String from, String to) {
        if (!CATALOG_BLOCK_TYPES.contains(block.getType())) {
            return null;
        }
        JsonNode root = parse(block.getConfig());
        if (root == null) {
            return null;
        }
        if (!replace(root, normalize(from), to)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize block config", e);
        }
    }

    public static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private JsonNode parse(String config) {
        if (config == null || config.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(config);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static void collect(JsonNode node, Set<String> out) {
        if (node.isTextual()) {
            String text = normalize(node.asText());
            if (!text.isEmpty()) {
                out.add(text);
            }
        } else if (node.isContainerNode()) {
            node.forEach(child -> collect(child, out));
        }
    }

    private static boolean replace(JsonNode node, String from, String to) {
        boolean changed = false;
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (value.isTextual() && normalize(value.asText()).equals(from)) {
                    field.setValue(TextNode.valueOf(to));
                    changed = true;
                } else {
                    changed |= replace(value, from, to);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                JsonNode value = array.get(i);
                if (value.isTextual() && normalize(value.asText()).equals(from)) {
                    array.set(i, TextNode.valueOf(to));
                    changed = true;
                } else {
                    changed |= replace(value, from, to);
                }
            }
        }
        return changed;
    }
}
