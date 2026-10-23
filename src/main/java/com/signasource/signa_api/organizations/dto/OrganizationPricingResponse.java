package com.signasource.signa_api.organizations.dto;

import java.math.BigDecimal;

public record OrganizationPricingResponse(BigDecimal pricePerPerson, String currency) {}
