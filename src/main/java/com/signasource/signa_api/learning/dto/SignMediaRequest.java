package com.signasource.signa_api.learning.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param meanings the signs to resolve
 * @param includeMissing opt-in (default false): also return an entry, with null {@code url} and
 *     {@code format}, for every requested sign that has no file. Older app versions cannot handle
 *     such entries, so they must not receive them unless they ask.
 */
public record SignMediaRequest(
        @NotEmpty(message = "At least one meaning is required") @Size(max = 200, message = "At most 200 meanings per request") List<String> meanings,
        Boolean includeMissing) {

    public boolean wantsMissing() {
        return Boolean.TRUE.equals(includeMissing);
    }
}
