package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.Handedness;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** New catalog sign from the admin panel; the animation is uploaded afterwards. */
public record CreateAdminSignRequest(
        @NotBlank(message = "The meaning is mandatory") String meaning,
        @NotNull(message = "Must specify if one or two handed") Handedness handedness,
        String youtubeUrl) {}
