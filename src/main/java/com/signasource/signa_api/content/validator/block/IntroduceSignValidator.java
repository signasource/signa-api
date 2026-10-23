package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.config.IntroduceSignConfig;
import com.signasource.signa_api.content.dto.validation.ValidationContext;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.util.BlockConfigParser;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class IntroduceSignValidator implements BlockValidator {

    private final BlockConfigParser parser;

    public IntroduceSignValidator(BlockConfigParser parser) {
        this.parser = parser;
    }

    @Override
    public BlockType supports() {
        return BlockType.INTRODUCE_SIGN;
    }

    @Override
    public void validate(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        Optional<IntroduceSignConfig> parsed =
                parser.parse(block.config(), IntroduceSignConfig.class);
        if (parsed.isEmpty()) {
            errors.add(
                    new ValidationError(ctx.location(), "invalid config for INTRODUCE_SIGN block"));
            return;
        }
        IntroduceSignConfig config = parsed.get();

        if (config.meaning() == null || config.meaning().isBlank()) {
            errors.add(new ValidationError(ctx.location(), "meaning is required"));
        }
        if (config.word() == null || config.word().isBlank()) {
            errors.add(new ValidationError(ctx.location(), "word is required"));
        }
    }
}
