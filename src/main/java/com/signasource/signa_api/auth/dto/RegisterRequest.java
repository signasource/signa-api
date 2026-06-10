package com.signasource.signa_api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@Email @NotBlank @Size(max = 255) String email, @NotBlank String password,
		@NotBlank @Size(min = 2, max = 100) @Pattern(regexp = "^[\\p{L} .'-]+$") String name) {
}
