package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.dto.SignLanguageResponse;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignLanguageServiceTest {

    @Mock private SignLanguageRepository signLanguageRepository;

    @InjectMocks private SignLanguageService signLanguageService;

    @Test
    void shouldReturnAllSignLanguagesMapped() {
        SignLanguage lsa =
                SignLanguage.builder()
                        .id(UUID.randomUUID())
                        .code("LSA")
                        .name("Lengua de Señas Argentina")
                        .countryCode("ARG")
                        .build();
        when(signLanguageRepository.findAll()).thenReturn(List.of(lsa));

        List<SignLanguageResponse> result = signLanguageService.getAll();

        assertEquals(1, result.size());
        SignLanguageResponse response = result.get(0);
        assertEquals(lsa.getId(), response.id());
        assertEquals("LSA", response.code());
        assertEquals("Lengua de Señas Argentina", response.name());
        assertEquals("ARG", response.countryCode());
        verify(signLanguageRepository).findAll();
    }

    @Test
    void shouldReturnEmptyListWhenNoSignLanguages() {
        when(signLanguageRepository.findAll()).thenReturn(List.of());

        assertTrue(signLanguageService.getAll().isEmpty());
        verify(signLanguageRepository).findAll();
    }
}
