package com.signasource.signa_api.content.validator.semantic;

import com.signasource.signa_api.content.loader.LoadedCourse;
import com.signasource.signa_api.content.validator.ValidationError;
import java.util.List;

/**
 * Cross-cutting domain validation over a whole {@link LoadedCourse} (references, consistency
 * between blocks, etc.), as opposed to structural or single-block checks. Implementations are
 * Spring beans auto-collected by {@code ContentValidator}. They accumulate into the shared {@code
 * errors} list rather than throwing, so all problems are reported at once.
 */
public interface SemanticValidator {

    void validate(LoadedCourse course, List<ValidationError> errors);
}
