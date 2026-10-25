package com.signasource.signa_api.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnBadRequestWhenRequiredParameterIsMissing() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMissingParameter(
                        new MissingServletRequestParameterException("signLanguageId", "UUID"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("signLanguageId: is required", response.getBody().message());
        assertEquals(400, response.getBody().status());
    }

    @Test
    void shouldReturnBadRequestWhenParameterHasInvalidType() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("signLanguageId");

        ResponseEntity<ErrorResponse> response = handler.handleArgumentTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("signLanguageId: invalid value", response.getBody().message());
        assertEquals(400, response.getBody().status());
    }

    @Test
    void shouldAnswer405ForAnUnsupportedMethod() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("POST"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(405, response.getBody().status());
    }
}
