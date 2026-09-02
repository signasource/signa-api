package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.config.R2Properties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class SignAnimationServiceTest {

    @Mock private S3Presigner r2Presigner;

    private R2Properties r2Properties;
    private SignAnimationService signAnimationService;

    @BeforeEach
    void setUp() {
        r2Properties =
                new R2Properties(
                        "https://account.r2.cloudflarestorage.com",
                        "key",
                        "secret",
                        "signa-animations",
                        15);
        signAnimationService = new SignAnimationService(r2Presigner, r2Properties);
    }

    @Test
    void shouldReturnPresignedUrlForObjectKey() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://r2.example/signed").toURL());
        when(r2Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presigned);

        String url = signAnimationService.presignedGetUrl("lsa/test.glb");

        assertEquals("https://r2.example/signed", url);

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(r2Presigner).presignGetObject(captor.capture());
        GetObjectPresignRequest request = captor.getValue();
        assertEquals(Duration.ofMinutes(15), request.signatureDuration());
        assertEquals("signa-animations", request.getObjectRequest().bucket());
        assertEquals("lsa/test.glb", request.getObjectRequest().key());
    }

    @Test
    void shouldPropagateExceptionWhenPresigningFails() {
        when(r2Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("R2 misconfigured"));

        assertThrows(
                RuntimeException.class, () -> signAnimationService.presignedGetUrl("lsa/test.glb"));
    }
}
