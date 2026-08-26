package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.config.InfoConfig;
import com.signasource.signa_api.content.dto.validation.ValidationContext;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.util.BlockConfigParser;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class InfoValidator implements BlockValidator {

    private final BlockConfigParser parser;

    public InfoValidator(BlockConfigParser parser) {
        this.parser = parser;
    }

    @Override
    public BlockType supports() {
        return BlockType.INFO;
    }

    @Override
    public void validate(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        Optional<InfoConfig> parsed = parser.parse(block.config(), InfoConfig.class);
        if (parsed.isEmpty()) {
            errors.add(new ValidationError(ctx.location(), "invalid config for INFO block"));
            return;
        }
    }
}
