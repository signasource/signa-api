package com.signasource.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.validation.ValidPasswordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ValidPasswordValidatorTest {
    private ValidPasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ValidPasswordValidator();
    }

    @Test
    void shouldAcceptPasswordWithAtLeast8Characters() {
        assertTrue(validator.isValid("password", null));
    }

    @Test
    void shouldAcceptSimplePasswordWithoutComplexityRules() {
        // Sin mayúsculas, dígitos ni símbolos: la única regla es la longitud.
        assertTrue(validator.isValid("abcdefgh", null));
    }

    @Test
    void shouldRejectNullPassword() {
        assertFalse(validator.isValid(null, null));
    }

    @Test
    void shouldRejectPasswordShorterThan8Characters() {
        assertFalse(validator.isValid("abc123!", null));
    }

    @Test
    void shouldRejectPasswordLongerThan72Characters() {
        String password = "a".repeat(73);

        assertFalse(validator.isValid(password, null));
    }
}
