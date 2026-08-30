package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.config.R2Properties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** Signs time-limited GET URLs for R2 objects so clients never see the R2 credentials. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignAnimationService {

    private final S3Presigner r2Presigner;
    private final R2Properties r2Properties;

    public String presignedGetUrl(String objectKey) {
        try {
            GetObjectRequest getObjectRequest =
                    GetObjectRequest.builder().bucket(r2Properties.bucket()).key(objectKey).build();

            GetObjectPresignRequest presignRequest =
                    GetObjectPresignRequest.builder()
                            .signatureDuration(
                                    Duration.ofMinutes(r2Properties.presignExpiryMinutes()))
                            .getObjectRequest(getObjectRequest)
                            .build();

            return r2Presigner.presignGetObject(presignRequest).url().toString();
        } catch (RuntimeException ex) {
            log.error("Failed to presign R2 object key '{}'", objectKey, ex);
            throw ex;
        }
    }
}
