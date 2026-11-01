package com.signasource.signa_api.organizations.dto;

import java.util.List;

/** A module (topic) of a purchasable course and the words of the signs it introduces. */
public record CatalogModuleResponse(String title, List<String> signs) {}
