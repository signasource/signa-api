package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.config.SpellNameConfig;
import com.signasource.signa_api.content.dto.validation.ValidationContext;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.util.BlockConfigParser;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SpellNameValidator implements BlockValidator {

    // Hard cap: the learner types the name and every letter is a recognition attempt, so a long
    // one turns the exercise into a slog.
    private static final int MAX_LETTERS = 12;

    private final BlockConfigParser parser;

    public SpellNameValidator(BlockConfigParser parser) {
        this.parser = parser;
    }

    @Override
    public BlockType supports() {
        return BlockType.SPELL_NAME;
    }

    @Override
    public void validate(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        Optional<SpellNameConfig> parsed = parser.parse(block.config(), SpellNameConfig.class);
        if (parsed.isEmpty()) {
            errors.add(new ValidationError(ctx.location(), "config is not valid for SPELL_NAME"));
            return;
        }

        Integer max = parsed.get().maxLetters();
        if (max != null && (max < 1 || max > MAX_LETTERS)) {
            errors.add(
                    new ValidationError(
                            ctx.location(), "max_letters must be between 1 and " + MAX_LETTERS));
        }
    }
}
