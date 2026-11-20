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
import java.util.Objects;
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
                    BlockType.MATCH);

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

    /**
     * For an {@code INTRODUCE_SIGN} block whose {@code meaning} is {@code meaning}: the config with
     * {@code video_url} set to {@code url} (removed when null), or {@code null} when the block is
     * not for that sign or already holds that value. The app reads the link from here, not from the
     * catalog, so the sign editor has to keep these copies in sync.
     */
    public String withVideoUrl(LessonBlock block, String meaning, String url) {
        if (block.getType() != BlockType.INTRODUCE_SIGN) {
            return null;
        }
        if (!(parse(block.getConfig()) instanceof ObjectNode object)) {
            return null;
        }
        JsonNode current = object.get("meaning");
        if (current == null
                || !current.isTextual()
                || !normalize(current.asText()).equals(normalize(meaning))) {
            return null;
        }
        JsonNode existing = object.get("video_url");
        String existingUrl = existing != null && existing.isTextual() ? existing.asText() : null;
        if (Objects.equals(existingUrl, url)) {
            return null;
        }
        if (url == null) {
            object.remove("video_url");
        } else {
            object.put("video_url", url);
        }
        try {
            return objectMapper.writeValueAsString(object);
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
