package com.signasource.signa_api.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimitInterceptorTest {

    private static final String CLIENT_IP = "192.168.1.1";
    private static final String FORWARDED_IP = "10.0.0.1";

    private RateLimitInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Object handler;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new RateLimitInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        handler = new Object();
        when(request.getRemoteAddr()).thenReturn(CLIENT_IP);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    }

    @Test
    void preHandle_withinLimit_returnsTrue() throws Exception {
        boolean result = interceptor.preHandle(request, response, handler);

        assertTrue(result);
    }

    @Test
    void preHandle_exceedingLimit_returnsFalseAndSets429() throws Exception {
        for (int i = 0; i < RateLimitInterceptor.BUCKET_CAPACITY; i++) {
            interceptor.preHandle(request, response, handler);
        }

        boolean result = interceptor.preHandle(request, response, handler);

        assertFalse(result);
        verify(response).setStatus(RateLimitInterceptor.TOO_MANY_REQUESTS);
    }

    @Test
    void preHandle_usesXForwardedForHeader_whenPresent() throws Exception {
        when(request.getHeader("X-Forwarded-For")).thenReturn(FORWARDED_IP + ", 172.16.0.1");

        boolean result = interceptor.preHandle(request, response, handler);

        assertTrue(result);
    }

    @Test
    void preHandle_differentIps_haveIndependentBuckets() throws Exception {
        HttpServletRequest otherRequest = mock(HttpServletRequest.class);
        when(otherRequest.getRemoteAddr()).thenReturn("10.0.0.2");

        for (int i = 0; i < RateLimitInterceptor.BUCKET_CAPACITY; i++) {
            interceptor.preHandle(request, response, handler);
        }

        boolean result = interceptor.preHandle(otherRequest, response, handler);

        assertTrue(result);
    }
}
