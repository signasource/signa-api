package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.LessonBlockDto;
import com.signasource.signa_api.content.validator.block.config.SelectSignConfig;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SelectSignValidator implements BlockValidator {

    private final BlockConfigParser parser;

    public SelectSignValidator(BlockConfigParser parser) {
        this.parser = parser;
    }

    @Override
    public BlockType supports() {
        return BlockType.SELECT_SIGN;
    }

    @Override
    public void validate(LessonBlockDto block, ValidationContext ctx, List<String> errors) {
        Optional<SelectSignConfig> parsed = parser.parse(block.config(), SelectSignConfig.class);
        if (parsed.isEmpty()) {
            errors.add(ctx.location() + ": invalid config for SELECT_SIGN block");
            return;
        }
        SelectSignConfig config = parsed.get();

        boolean wordValid = config.word() != null && !config.word().isBlank();
        if (!wordValid) {
            errors.add(ctx.location() + ": word is required");
        }

        if (config.options() == null) {
            errors.add(ctx.location() + ": options is required");
        } else {
            if (config.options().size() < 2) {
                errors.add(ctx.location() + ": options must have at least 2 elements");
            }
            if (wordValid && !config.options().contains(config.word())) {
                errors.add(ctx.location() + ": word must be one of the options");
            }
        }
    }
}
