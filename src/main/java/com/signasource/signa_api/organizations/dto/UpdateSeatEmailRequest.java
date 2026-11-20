package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateSeatEmailRequest(@NotBlank @Email String email) {}
