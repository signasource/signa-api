package com.signasource.signa_api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of a back-office admin: a null field is left as is. {@code avatar} is a data URL;
 * an empty string removes the photo. The password is replaced without asking for the current one
 * (any admin has full access to the panel).
 */
public record UpdateAdminRequest(
        @Size(min = 1, max = 100) String name,
        @Size(min = 1, max = 100) String lastName,
        @Email @Size(max = 254) String email,
        @Size(min = 8, max = 72) String password,
        @Size(max = 400_000) @Pattern(
                        regexp = "^$|^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$",
                        message = "Avatar must be a base64 PNG, JPEG or WebP data URL")
                String avatar) {}
