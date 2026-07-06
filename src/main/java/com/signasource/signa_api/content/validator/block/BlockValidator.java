package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.LessonBlockDto;
import com.signasource.signa_api.content.validator.ValidationError;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;

public interface BlockValidator {

    BlockType supports();

    void validate(LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors);
}
