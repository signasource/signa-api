package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CreateSignRequest;
import com.signasource.signa_api.learning.dto.SignSummaryResponse;
import com.signasource.signa_api.learning.entity.Handedness;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SignServiceTest {

    @Mock private SignRepository signRepository;

    @Mock private SignLanguageRepository signLanguageRepository;

    @InjectMocks private SignService signService;

    private UUID signLanguageId;
    private SignLanguage signLanguage;
    private Sign sign;
    private CreateSignRequest createRequest;

    @BeforeEach
    void setUp() {
        signLanguageId = UUID.randomUUID();
        signLanguage =
                SignLanguage.builder()
                        .id(signLanguageId)
                        .code("LSA")
                        .name("Lengua de Señas Argentina")
                        .build();

        sign =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning("Hola")
                        .description("Saludo básico")
                        .handedness(Handedness.ONE_HANDED)
                        .signLanguage(signLanguage)
                        .build();

        createRequest =
                new CreateSignRequest(
                        "Hola", "Saludo básico", signLanguageId, Handedness.ONE_HANDED, "url.mp4");
    }

    @Test
    void shouldReturnSignsCatalogWithoutQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Sign> signPage = new PageImpl<>(List.of(sign));

        when(signRepository.findBySignLanguageId(eq(signLanguageId), any(Pageable.class)))
                .thenReturn(signPage);

        Page<SignSummaryResponse> response =
                signService.getSignsCatalog(signLanguageId, null, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Hola", response.getContent().getFirst().meaning());

        verify(signRepository).findBySignLanguageId(signLanguageId, pageable);
        verify(signRepository, never())
                .findBySignLanguageIdAndMeaningContainingIgnoreCase(any(), any(), any());
    }

    @Test
    void shouldReturnSignsCatalogWithQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Sign> signPage = new PageImpl<>(List.of(sign));

        when(signRepository.findBySignLanguageIdAndMeaningContainingIgnoreCase(
                        eq(signLanguageId), eq("Hola"), any(Pageable.class)))
                .thenReturn(signPage);

        Page<SignSummaryResponse> response =
                signService.getSignsCatalog(signLanguageId, "Hola", pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());

        verify(signRepository)
                .findBySignLanguageIdAndMeaningContainingIgnoreCase(
                        signLanguageId, "Hola", pageable);
    }

    @Test
    void shouldCreateSignSuccessfully() {
        when(signLanguageRepository.findById(signLanguageId)).thenReturn(Optional.of(signLanguage));
        when(signRepository.save(any(Sign.class))).thenReturn(sign);

        SignSummaryResponse response = signService.createSign(createRequest);

        assertNotNull(response);
        assertEquals("Hola", response.meaning());
        assertEquals(Handedness.ONE_HANDED.name(), response.handedness());

        verify(signLanguageRepository).findById(signLanguageId);
        verify(signRepository).save(any(Sign.class));
    }

    @Test
    void shouldThrowExceptionWhenCreatingSignWithInvalidLanguage() {
        when(signLanguageRepository.findById(signLanguageId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> signService.createSign(createRequest));

        verify(signLanguageRepository).findById(signLanguageId);
        verify(signRepository, never()).save(any());
    }
}
