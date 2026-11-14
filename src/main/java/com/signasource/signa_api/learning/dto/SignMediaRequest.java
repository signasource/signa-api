package com.signasource.signa_api.learning.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SignMediaRequest(
        @NotEmpty(message = "At least one meaning is required") @Size(max = 200, message = "At most 200 meanings per request") List<String> meanings) {}
