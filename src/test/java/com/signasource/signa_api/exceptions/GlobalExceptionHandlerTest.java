package com.signasource.signa_api.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.HttpRequestMethodNotSupportedException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldAnswer405ForAnUnsupportedMethod() {
        var response =
                handler.handleMethodNotSupported(
                        new HttpRequestMethodNotSupportedException("POST"));

        assertEquals(405, response.getStatusCode().value());
        assertEquals(405, response.getBody().status());
    }
}
