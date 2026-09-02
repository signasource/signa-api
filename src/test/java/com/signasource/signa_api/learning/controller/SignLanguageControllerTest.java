package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.dto.SignLanguageResponse;
import com.signasource.signa_api.learning.service.SignLanguageService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class SignLanguageControllerTest {

    @Mock private SignLanguageService signLanguageService;

    @InjectMocks private SignLanguageController signLanguageController;

    @Test
    void getSignLanguages_ShouldReturn200WithBody() {
        List<SignLanguageResponse> languages =
                List.of(
                        new SignLanguageResponse(
                                UUID.randomUUID(), "LSA", "Lengua de Señas Argentina", "ARG"));
        when(signLanguageService.getAll()).thenReturn(languages);

        ResponseEntity<List<SignLanguageResponse>> response =
                signLanguageController.getSignLanguages();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(languages, response.getBody());
        verify(signLanguageService).getAll();
    }
}
