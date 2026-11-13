package com.signasource.signa_api.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A PNG/JPEG/WebP photo as a base64 data URL. Clients resize it down before sending. */
public record UpdateAvatarRequest(
        @NotBlank @Size(max = UpdateAvatarRequest.MAX_LENGTH) @Pattern(
                        regexp = "^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$",
                        message = "Avatar must be a base64 PNG, JPEG or WebP data URL")
                String avatar) {

    /** About 110 KB of image: a 512px JPEG fits comfortably; the app sends far less. */
    public static final int MAX_LENGTH = 150_000;
}
