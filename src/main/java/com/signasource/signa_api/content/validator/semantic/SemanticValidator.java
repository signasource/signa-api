package com.signasource.signa_api.content.validator.semantic;

import com.signasource.signa_api.content.loader.LoadedCourse;
import java.util.List;

public interface SemanticValidator {

    void validate(LoadedCourse course, List<ValidationError> errors);
}
