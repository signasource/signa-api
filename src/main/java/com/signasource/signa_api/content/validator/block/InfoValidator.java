package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.LessonBlockDto;
import com.signasource.signa_api.content.validator.block.config.InfoConfig;
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
    public void validate(LessonBlockDto block, ValidationContext ctx, List<String> errors) {
        Optional<InfoConfig> parsed = parser.parse(block.config(), InfoConfig.class);
        if (parsed.isEmpty()) {
            errors.add(ctx.location() + ": invalid config for INFO block");
            return;
        }
        InfoConfig config = parsed.get();
        if (config.text() == null || config.text().isBlank()) {
            errors.add(ctx.location() + ": text is required");
        }
    }
}
