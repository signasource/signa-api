package com.signasource.signa_api.notification.entity;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores an optional {@code Map<String, String>} of metadata as a JSON string.
 * Using a converter (instead of a native {@code jsonb} column) keeps the
 * mapping portable across PostgreSQL (prod) and H2 (tests).
 */
@Converter
public class StringMapConverter implements AttributeConverter<Map<String, String>, String> {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(Map<String, String> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return null;
		}
		try {
			return MAPPER.writeValueAsString(attribute);
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not serialize notification metadata", e);
		}
	}

	@Override
	public Map<String, String> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readValue(dbData, TYPE);
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read notification metadata", e);
		}
	}
}
