package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.config.ContextResponseConfig;
import com.signasource.signa_api.content.dto.validation.ValidationContext;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.util.BlockConfigParser;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ContextResponseValidator implements BlockValidator {

    private final BlockConfigParser parser;

    public ContextResponseValidator(BlockConfigParser parser) {
        this.parser = parser;
    }

    @Override
    public BlockType supports() {
        return BlockType.CONTEXT_RESPONSE;
    }

    @Override
    public void validate(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        Optional<ContextResponseConfig> parsed =
                parser.parse(block.config(), ContextResponseConfig.class);
        if (parsed.isEmpty()) {
            errors.add(
                    new ValidationError(
                            ctx.location(), "invalid config for CONTEXT_RESPONSE block"));
            return;
        }
        ContextResponseConfig config = parsed.get();

        if (config.question() == null || config.question().isBlank()) {
            errors.add(new ValidationError(ctx.location(), "question is required"));
        }

        boolean answerValid = config.answer() != null && !config.answer().isBlank();
        if (!answerValid) {
            errors.add(new ValidationError(ctx.location(), "answer is required"));
        }

        if (config.options() == null) {
            errors.add(new ValidationError(ctx.location(), "options is required"));
        } else {
            if (config.options().size() < 2) {
                errors.add(
                        new ValidationError(
                                ctx.location(), "options must have at least 2 elements"));
            }
            if (answerValid && !config.options().contains(config.answer())) {
                errors.add(
                        new ValidationError(ctx.location(), "answer must be one of the options"));
            }
        }
    }
}
