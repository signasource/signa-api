package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.validation.ValidationContext;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SpellNameValidator implements BlockValidator {

    @Override
    public BlockType supports() {
        return BlockType.SPELL_NAME;
    }

    @Override
    public void validate(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        // No required fields — max_letters is now hardcoded to 10 in the app layer.
    }
}
