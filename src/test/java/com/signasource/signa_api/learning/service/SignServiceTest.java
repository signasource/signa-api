package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.signasource.signa_api.config.R2Properties;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.dto.CreateSignRequest;
import com.signasource.signa_api.learning.dto.SignAnimationResponse;
import com.signasource.signa_api.learning.dto.SignSummaryResponse;
import com.signasource.signa_api.learning.entity.Handedness;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock private SignAnimationService signAnimationService;

    private R2Properties r2Properties;
    private SignService signService;

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
                        .handedness(Handedness.ONE_HANDED)
                        .signLanguage(signLanguage)
                        .build();

        createRequest =
                new CreateSignRequest("Hola", signLanguageId, Handedness.ONE_HANDED, "url.mp4");

        r2Properties = new R2Properties(null, null, null, "signa-animations", 15);
        signService =
                new SignService(
                        signRepository, signLanguageRepository, signAnimationService, r2Properties);
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
        when(signRepository.existsByMeaning("Hola")).thenReturn(false);
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
        when(signRepository.existsByMeaning("Hola")).thenReturn(false);
        when(signLanguageRepository.findById(signLanguageId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> signService.createSign(createRequest));

        verify(signLanguageRepository).findById(signLanguageId);
        verify(signRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenCreatingSignWithDuplicateMeaning() {
        when(signRepository.existsByMeaning("Hola")).thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class, () -> signService.createSign(createRequest));

        verify(signRepository, never()).save(any());
        verifyNoInteractions(signLanguageRepository);
    }

    @Test
    void shouldReturnSignAnimationWhenAnimationUrlPresent() {
        UUID signId = UUID.randomUUID();
        Sign animatedSign =
                Sign.builder()
                        .id(signId)
                        .meaning("test")
                        .handedness(Handedness.ONE_HANDED)
                        .animationUrl("lsa/test.glb")
                        .signLanguage(signLanguage)
                        .build();
        when(signRepository.findByMeaning("test")).thenReturn(Optional.of(animatedSign));
        when(signAnimationService.presignedGetUrl("lsa/test.glb"))
                .thenReturn("https://r2.example/signed");

        SignAnimationResponse response = signService.getSignAnimation("test");

        assertEquals(signId, response.signId());
        assertEquals("https://r2.example/signed", response.animationUrl());
        assertEquals(15 * 60L, response.expiresInSeconds());
        verify(signAnimationService).presignedGetUrl("lsa/test.glb");
    }

    @Test
    void shouldThrowNotFoundWhenSignDoesNotExistForAnimation() {
        when(signRepository.findByMeaning("missing")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> signService.getSignAnimation("missing"));

        verifyNoInteractions(signAnimationService);
    }

    @Test
    void shouldThrowNotFoundWhenSignHasBlankAnimationUrl() {
        Sign withoutAnimation =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning("Hola")
                        .handedness(Handedness.ONE_HANDED)
                        .animationUrl("   ")
                        .signLanguage(signLanguage)
                        .build();
        when(signRepository.findByMeaning("Hola")).thenReturn(Optional.of(withoutAnimation));

        assertThrows(NotFoundException.class, () -> signService.getSignAnimation("Hola"));

        verifyNoInteractions(signAnimationService);
    }

    @Test
    void shouldReturnAnimationUrlsByMeaningForBatchLookup() {
        Sign withAnimation =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning("Hola")
                        .handedness(Handedness.ONE_HANDED)
                        .animationUrl("lsa/hola.glb")
                        .signLanguage(signLanguage)
                        .build();
        Sign withoutAnimation =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning("Gracias")
                        .handedness(Handedness.ONE_HANDED)
                        .signLanguage(signLanguage)
                        .build();
        when(signRepository.findByMeaningIn(List.of("Hola", "Gracias")))
                .thenReturn(List.of(withAnimation, withoutAnimation));
        when(signAnimationService.presignedGetUrl("lsa/hola.glb"))
                .thenReturn("https://r2.example/hola");

        Map<String, String> response = signService.getSignAnimations(List.of("Hola", "Gracias"));

        assertEquals(1, response.size());
        assertEquals("https://r2.example/hola", response.get("Hola"));
        verify(signAnimationService).presignedGetUrl("lsa/hola.glb");
    }
}
