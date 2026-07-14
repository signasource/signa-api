package com.signasource.signa_api.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUsernameRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9_]+$") String username) {}
