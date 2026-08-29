package com.signasource.signa_api.content.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestSignSeederTest {

    private static final String DEMO_MEANING = "test";
    private static final String DEMO_ANIMATION_KEY = "lsa/test.glb";

    @Mock private SignRepository signRepository;

    @Mock private SignLanguageRepository signLanguageRepository;

    @InjectMocks private TestSignSeeder seeder;

    @Test
    void shouldCreateDemoSignWhenAbsent() {
        SignLanguage lsa = SignLanguage.builder().id(UUID.randomUUID()).code("LSA").build();
        when(signRepository.findByMeaning(DEMO_MEANING)).thenReturn(Optional.empty());
        when(signLanguageRepository.findByCode("LSA")).thenReturn(Optional.of(lsa));
        when(signRepository.save(any(Sign.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run(null);

        ArgumentCaptor<Sign> captor = ArgumentCaptor.forClass(Sign.class);
        verify(signRepository).save(captor.capture());
        assertEquals(DEMO_MEANING, captor.getValue().getMeaning());
        assertEquals(DEMO_ANIMATION_KEY, captor.getValue().getAnimationUrl());
        assertEquals(lsa, captor.getValue().getSignLanguage());
    }

    @Test
    void shouldNotCreateDemoSignWhenAlreadyPresent() {
        Sign existing =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning(DEMO_MEANING)
                        .animationUrl(DEMO_ANIMATION_KEY)
                        .build();
        when(signRepository.findByMeaning(DEMO_MEANING)).thenReturn(Optional.of(existing));

        seeder.run(null);

        verify(signRepository, never()).save(any());
        verifyNoInteractions(signLanguageRepository);
    }

    @Test
    void shouldSkipWhenSignLanguageMissing() {
        when(signRepository.findByMeaning(DEMO_MEANING)).thenReturn(Optional.empty());
        when(signLanguageRepository.findByCode("LSA")).thenReturn(Optional.empty());

        seeder.run(null);

        verify(signRepository, never()).save(any());
    }
}
