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
    void shouldAcceptValidPassword() {
        assertTrue(validator.isValid("Password123!", null));
    }

    @Test
    void shouldRejectNullPassword() {
        assertFalse(validator.isValid(null, null));
    }

    @Test
    void shouldRejectPasswordShorterThan8Characters() {
        assertFalse(validator.isValid("Ab1!", null));
    }

    @Test
    void shouldRejectPasswordWithoutUppercaseLetter() {
        assertFalse(validator.isValid("password123!", null));
    }

    @Test
    void shouldRejectPasswordWithoutLowercaseLetter() {
        assertFalse(validator.isValid("PASSWORD123!", null));
    }

    @Test
    void shouldRejectPasswordWithoutNumber() {
        assertFalse(validator.isValid("Password!", null));
    }

    @Test
    void shouldRejectPasswordWithoutSpecialCharacter() {
        assertFalse(validator.isValid("Password123", null));
    }

    @Test
    void shouldRejectPasswordLongerThan72Characters() {
        String password = "Password123!" + "a".repeat(61);

        assertFalse(validator.isValid(password, null));
    }
}
