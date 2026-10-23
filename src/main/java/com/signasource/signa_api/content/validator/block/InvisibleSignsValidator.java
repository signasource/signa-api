package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.config.InvisibleSignsConfig;
import com.signasource.signa_api.content.dto.validation.ValidationContext;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.util.BlockConfigParser;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InvisibleSignsValidator implements BlockValidator {

    private final BlockConfigParser parser;

    public InvisibleSignsValidator(BlockConfigParser parser) {
        this.parser = parser;
    }

    @Override
    public BlockType supports() {
        return BlockType.INVISIBLE_SIGNS;
    }

    @Override
    public void validate(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        Optional<InvisibleSignsConfig> parsed =
                parser.parse(block.config(), InvisibleSignsConfig.class);
        if (parsed.isEmpty()) {
            errors.add(
                    new ValidationError(
                            ctx.location(), "invalid config for INVISIBLE_SIGNS block"));
            return;
        }
        InvisibleSignsConfig config = parsed.get();

        if (config.signs() == null || config.signs().isEmpty()) {
            errors.add(new ValidationError(ctx.location(), "signs must have at least 1 element"));
        }
    }
}
