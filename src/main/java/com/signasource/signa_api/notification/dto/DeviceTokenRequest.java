package com.signasource.signa_api.notification.dto;

import com.signasource.signa_api.notification.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceTokenRequest(
        @NotBlank @Size(max = 4096) String token, DevicePlatform platform) {}
