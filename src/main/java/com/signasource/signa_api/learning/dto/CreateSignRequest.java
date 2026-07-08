package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Handedness;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateSignRequest(
    @NotBlank(message = "The meaning is mandatory")
    String meaning,

    String description,

    @NotNull(message = "Sign language ID is mandatory")
    UUID signLanguageId,

    @NotNull(message = "Must specify if one or two handed")
    Handedness handedness,

    String animationUrl
) {}
