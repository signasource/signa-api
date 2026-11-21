package com.signasource.signa_api.organizations.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RegisterOrganizationRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static RegisterOrganizationRequest withPhone(String phone) {
        return new RegisterOrganizationRequest(
                "Empresa", "30-12345678-9", "admin@empresa.com", phone, "Admin", "password123");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "+54 11 1234-5678",
                "+54 9 11 1234-5678",
                "11 1234-5678",
                "(011) 4123.4567",
                "1112345678"
            })
    void shouldAcceptValidPhone(String phone) {
        assertTrue(validator.validate(withPhone(phone)).isEmpty());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "   ",
                "1234567",
                "abc12345678",
                "+54 11 1234 5678 9999 9",
                "11+12345678"
            })
    void shouldRejectMissingOrInvalidPhone(String phone) {
        assertTrue(
                validator.validate(withPhone(phone)).stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("contactPhone")));
    }
}
