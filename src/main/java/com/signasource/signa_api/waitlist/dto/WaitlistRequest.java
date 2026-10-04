package com.signasource.signa_api.waitlist.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WaitlistRequest(@NotBlank @Email @Size(max = 254) String email) {}
