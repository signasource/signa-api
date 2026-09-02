package com.signasource.signa_api.learning.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SignAnimationsRequest(@NotEmpty(message = "At least one meaning is required") List<String> meanings) {}
