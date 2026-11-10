package com.signasource.signa_api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code avatar} is optional: a PNG/JPEG/WebP data URL, or null/empty for no photo. */
public record CreateAdminRequest(
        @NotBlank @Email String email,
        @NotBlank String name,
        @NotBlank String lastName,
        @NotBlank String password,
        @Size(max = AvatarData.MAX_LENGTH) @Pattern(
                        regexp = AvatarData.PATTERN,
                        message = "Avatar must be a base64 PNG, JPEG or WebP data URL")
                String avatar) {}
