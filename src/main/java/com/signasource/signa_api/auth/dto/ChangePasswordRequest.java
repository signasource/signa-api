package com.signasource.signa_api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.signasource.signa_api.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @JsonProperty("current_password") 
        @NotBlank 
        String currentPassword, 
        
        @JsonProperty("new_password") 
        @ValidPassword 
        String newPassword
) {}