package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreatePurchaseRequest(
        @NotNull UUID courseId,
        @NotNull Integer durationDays,
        @NotEmpty List<@NotBlank @Email String> emails) {}
