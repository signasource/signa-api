package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.config.R2Properties;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.dto.CreateSignRequest;
import com.signasource.signa_api.learning.dto.SignAnimationResponse;
import com.signasource.signa_api.learning.dto.SignSummaryResponse;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignService {

    private final SignRepository signRepository;
    private final SignLanguageRepository signLanguageRepository;
    private final SignAnimationService signAnimationService;
    private final R2Properties r2Properties;

    @Transactional(readOnly = true)
    public Page<SignSummaryResponse> getSignsCatalog(
            UUID signLanguageId, String query, Pageable pageable) {
        Page<Sign> signs;

        if (query != null && !query.isBlank()) {
            signs =
                    signRepository.findBySignLanguageIdAndMeaningContainingIgnoreCase(
                            signLanguageId, query, pageable);
        } else {
            signs = signRepository.findBySignLanguageId(signLanguageId, pageable);
        }

        return signs.map(SignSummaryResponse::from);
    }

    @Transactional
    public SignSummaryResponse createSign(CreateSignRequest request) {
        if (signRepository.existsByMeaning(request.meaning())) {
            throw new ResourceAlreadyInUseException("Sign meaning already in use");
        }

        SignLanguage signLanguage =
                signLanguageRepository
                        .findById(request.signLanguageId())
                        .orElseThrow(() -> new NotFoundException("Sign language not found"));

        Sign sign =
                Sign.builder()
                        .meaning(request.meaning())
                        .handedness(request.handedness())
                        .animationUrl(request.animationUrl())
                        .signLanguage(signLanguage)
                        .build();

        Sign savedSign = signRepository.save(sign);
        return SignSummaryResponse.from(savedSign);
    }

    @Transactional(readOnly = true)
    public SignAnimationResponse getSignAnimation(String meaning) {
        Sign sign =
                signRepository
                        .findByMeaning(meaning)
                        .orElseThrow(() -> new NotFoundException("Sign not found"));

        String objectKey = sign.getAnimationUrl();
        if (objectKey == null || objectKey.isBlank()) {
            throw new NotFoundException("Sign has no animation");
        }

        String url = signAnimationService.presignedGetUrl(objectKey);
        return new SignAnimationResponse(
                sign.getId(), url, r2Properties.presignExpiryMinutes() * 60L);
    }

    /**
     * Batched lookup used by the lesson player to preload every sign animation it needs in a single
     * round trip. Meanings with no matching sign, or with no animation uploaded yet, are simply
     * omitted from the result.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getSignAnimations(List<String> meanings) {
        Map<String, String> urlsByMeaning = new HashMap<>();

        for (Sign sign : signRepository.findByMeaningIn(meanings)) {
            String objectKey = sign.getAnimationUrl();
            if (objectKey != null && !objectKey.isBlank()) {
                urlsByMeaning.put(
                        sign.getMeaning(), signAnimationService.presignedGetUrl(objectKey));
            }
        }

        return urlsByMeaning;
    }
}
