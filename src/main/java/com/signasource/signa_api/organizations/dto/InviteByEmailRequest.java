package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record InviteByEmailRequest(@NotBlank @Email String email, @Future Instant expiresAt) {}
