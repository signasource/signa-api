package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.LessonBlockDto;
import com.signasource.signa_api.content.validator.ValidationError;
import com.signasource.signa_api.content.validator.block.config.SelectMeaningConfig;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SelectMeaningValidator implements BlockValidator {

    private final BlockConfigParser parser;

    public SelectMeaningValidator(BlockConfigParser parser) {
        this.parser = parser;
    }

    @Override
    public BlockType supports() {
        return BlockType.SELECT_MEANING;
    }

    @Override
    public void validate(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        Optional<SelectMeaningConfig> parsed =
                parser.parse(block.config(), SelectMeaningConfig.class);
        if (parsed.isEmpty()) {
            errors.add(
                    new ValidationError(ctx.location(), "invalid config for SELECT_MEANING block"));
            return;
        }
        SelectMeaningConfig config = parsed.get();

        boolean signValid = config.sign() != null && !config.sign().isBlank();
        if (!signValid) {
            errors.add(new ValidationError(ctx.location(), "sign is required"));
        }

        if (config.options() == null) {
            errors.add(new ValidationError(ctx.location(), "options is required"));
        } else {
            if (config.options().size() < 2) {
                errors.add(
                        new ValidationError(
                                ctx.location(), "options must have at least 2 elements"));
            }
            if (signValid && !config.options().contains(config.sign())) {
                errors.add(new ValidationError(ctx.location(), "sign must be one of the options"));
            }
        }
    }
}
