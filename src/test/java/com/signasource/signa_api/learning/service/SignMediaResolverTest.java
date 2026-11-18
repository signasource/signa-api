package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignMediaResolverTest {

    @Mock private SignMediaStorage storage;

    private SignMediaResolver resolver() {
        return new SignMediaResolver(storage);
    }

    @Test
    void shouldFindAVideoOnlySignWithItsLateralTake() {
        when(storage.listKeys("lsa/"))
                .thenReturn(List.of("lsa/hombre.mp4", "lsa/hombre_lateral.mp4"));

        SignMediaResolver.Resolved found = resolver().resolve("hombre").orElseThrow();

        assertEquals("lsa/hombre.mp4", found.key());
        assertEquals("lsa/hombre_lateral.mp4", found.lateralKey());
    }

    @Test
    void shouldPreferTheAvatarAndIgnoreCaseAndAccents() {
        when(storage.listKeys("lsa/")).thenReturn(List.of("lsa/Clínica.mp4", "lsa/Clínica.glb"));

        SignMediaResolver.Resolved found = resolver().resolve("clinica").orElseThrow();

        assertEquals("lsa/Clínica.glb", found.key());
    }

    @Test
    void shouldResolveFileNameAliases() {
        when(storage.listKeys("lsa/")).thenReturn(List.of("lsa/nene.mp4", "lsa/nena.mp4"));
        SignMediaResolver resolver = resolver();

        assertEquals("lsa/nene.mp4", resolver.resolve("niño").orElseThrow().key());
        assertEquals("lsa/nena.mp4", resolver.resolve("Niña").orElseThrow().key());
    }

    @Test
    void shouldAskForTheExactKeyWhenTheBucketCannotBeListed() {
        when(storage.listKeys("lsa/")).thenReturn(List.of());
        when(storage.exists("lsa/hola.glb")).thenReturn(true);

        assertEquals("lsa/hola.glb", resolver().resolve("hola").orElseThrow().key());
    }

    @Test
    void shouldAnswerEmptyAndRememberIt() {
        when(storage.listKeys("lsa/")).thenReturn(List.of("lsa/otra.glb"));
        SignMediaResolver resolver = resolver();

        assertTrue(resolver.resolve("nada").isEmpty());
        assertEquals(Optional.empty(), resolver.resolve("nada"));
        verify(storage, times(1)).listKeys("lsa/");
    }

    @Test
    void shouldSurviveAFailingListing() {
        when(storage.listKeys("lsa/")).thenThrow(new IllegalStateException("r2 down"));

        assertTrue(resolver().resolve("hola").isEmpty());
    }
}
