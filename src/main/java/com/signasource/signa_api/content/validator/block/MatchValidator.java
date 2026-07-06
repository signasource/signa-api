package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.LessonBlockDto;
import com.signasource.signa_api.content.validator.ValidationError;
import com.signasource.signa_api.content.validator.block.config.MatchConfig;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MatchValidator implements BlockValidator {

    private final BlockConfigParser parser;

    public MatchValidator(BlockConfigParser parser) {
        this.parser = parser;
    }

    @Override
    public BlockType supports() {
        return BlockType.MATCH;
    }

    @Override
    public void validate(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        Optional<MatchConfig> parsed = parser.parse(block.config(), MatchConfig.class);
        if (parsed.isEmpty()) {
            errors.add(new ValidationError(ctx.location(), "invalid config for MATCH block"));
            return;
        }
        MatchConfig config = parsed.get();

        if (config.concepts() == null) {
            errors.add(new ValidationError(ctx.location(), "concepts is required"));
        } else if (config.concepts().size() < 2) {
            errors.add(
                    new ValidationError(ctx.location(), "concepts must have at least 2 elements"));
        }
    }
}
