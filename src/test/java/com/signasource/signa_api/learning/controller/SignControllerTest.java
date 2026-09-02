package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.dto.CreateSignRequest;
import com.signasource.signa_api.learning.dto.SignAnimationResponse;
import com.signasource.signa_api.learning.dto.SignSummaryResponse;
import com.signasource.signa_api.learning.entity.Handedness;
import com.signasource.signa_api.learning.service.SignService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class SignControllerTest {

    @Mock private SignService signService;

    @InjectMocks private SignController signController;

    private final UUID signLanguageId = UUID.randomUUID();

    @Test
    void testGetSigns() {
        Pageable pageable = PageRequest.of(0, 10);
        SignSummaryResponse summary =
                new SignSummaryResponse(UUID.randomUUID(), "Hola", "ONE_HANDED", "url");
        Page<SignSummaryResponse> mockPage = new PageImpl<>(List.of(summary));

        when(signService.getSignsCatalog(eq(signLanguageId), eq("Hola"), any(Pageable.class)))
                .thenReturn(mockPage);

        ResponseEntity<Page<SignSummaryResponse>> response =
                signController.getSigns(signLanguageId, "Hola", pageable);

        verify(signService).getSignsCatalog(signLanguageId, "Hola", pageable);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getContent().size());
    }

    @Test
    void testCreateSign() {
        CreateSignRequest request =
                new CreateSignRequest("Hola", signLanguageId, Handedness.ONE_HANDED, "url");
        SignSummaryResponse summary =
                new SignSummaryResponse(UUID.randomUUID(), "Hola", "ONE_HANDED", "url");

        when(signService.createSign(any(CreateSignRequest.class))).thenReturn(summary);

        ResponseEntity<SignSummaryResponse> response = signController.createSign(request);

        verify(signService).createSign(request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(summary, response.getBody());
    }

    @Test
    void testGetSignAnimation() {
        SignAnimationResponse animation =
                new SignAnimationResponse(UUID.randomUUID(), "https://r2.example/signed", 900L);

        when(signService.getSignAnimation("test")).thenReturn(animation);

        ResponseEntity<SignAnimationResponse> response = signController.getSignAnimation("test");

        verify(signService).getSignAnimation("test");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(animation, response.getBody());
    }
}
