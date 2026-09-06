package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.config.R2Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignAnimationService {

    private final R2Properties r2Properties;

    public String publicUrl(String objectKey) {
        return r2Properties.publicBaseUrl() + "/" + objectKey;
    }
}
