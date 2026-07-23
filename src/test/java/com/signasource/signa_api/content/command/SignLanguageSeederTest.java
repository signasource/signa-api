package com.signasource.signa_api.content.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class SignLanguageSeederTest {

    @Mock private SignLanguageRepository signLanguageRepository;

    @Test
    void shouldSeedLsaWhenMissing() {
        when(signLanguageRepository.findByCode("LSA")).thenReturn(Optional.empty());

        new SignLanguageSeeder(signLanguageRepository).run(new DefaultApplicationArguments());

        ArgumentCaptor<SignLanguage> captor = ArgumentCaptor.forClass(SignLanguage.class);
        verify(signLanguageRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("LSA");
    }

    @Test
    void shouldNotSeedWhenAlreadyPresent() {
        when(signLanguageRepository.findByCode("LSA"))
                .thenReturn(Optional.of(SignLanguage.builder().code("LSA").build()));

        new SignLanguageSeeder(signLanguageRepository).run(new DefaultApplicationArguments());

        verify(signLanguageRepository, never()).save(any());
    }
}
