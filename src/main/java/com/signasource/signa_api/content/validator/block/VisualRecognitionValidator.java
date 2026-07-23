package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.config.VisualRecognitionConfig;
import com.signasource.signa_api.content.dto.validation.ValidationContext;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.util.BlockConfigParser;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class VisualRecognitionValidator implements BlockValidator {

    private final BlockConfigParser parser;

    public VisualRecognitionValidator(BlockConfigParser parser) {
        this.parser = parser;
    }

    @Override
    public BlockType supports() {
        return BlockType.VISUAL_RECOGNITION;
    }

    @Override
    public void validate(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        Optional<VisualRecognitionConfig> parsed =
                parser.parse(block.config(), VisualRecognitionConfig.class);
        if (parsed.isEmpty()) {
            errors.add(
                    new ValidationError(
                            ctx.location(), "invalid config for VISUAL_RECOGNITION block"));
            return;
        }
        VisualRecognitionConfig config = parsed.get();

        if (config.signSequence() == null) {
            errors.add(new ValidationError(ctx.location(), "signSequence is required"));
        } else if (config.signSequence().isEmpty()) {
            errors.add(
                    new ValidationError(
                            ctx.location(), "signSequence must have at least 1 element"));
        }

        if (config.options() == null) {
            errors.add(new ValidationError(ctx.location(), "options is required"));
        } else if (config.options().isEmpty()) {
            errors.add(new ValidationError(ctx.location(), "options must have at least 1 element"));
        }

        if (config.keepOrder() == null) {
            errors.add(new ValidationError(ctx.location(), "keepOrder is required"));
        }
    }
}
