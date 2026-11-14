package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.config.R2Properties;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.dto.CreateSignRequest;
import com.signasource.signa_api.learning.dto.SignAnimationResponse;
import com.signasource.signa_api.learning.dto.SignMediaResponse;
import com.signasource.signa_api.learning.dto.SignSummaryResponse;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final SignMediaStorage signMediaStorage;

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
        if (signRepository.existsByMeaningIgnoreCase(request.meaning())) {
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
                        .youtubeUrl(request.youtubeUrl())
                        .signLanguage(signLanguage)
                        .build();

        Sign savedSign = signRepository.save(sign);
        return SignSummaryResponse.from(savedSign);
    }

    @Transactional(readOnly = true)
    public SignAnimationResponse getSignAnimation(String meaning) {
        Sign sign =
                signRepository
                        .findByMeaningIgnoreCase(meaning)
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

    /**
     * Public media URL and format of each requested sign, for the app's lesson player. Matching is
     * case-insensitive and each entry echoes the meaning as requested. Signs that do not exist or
     * have no animation are omitted: the client falls back to the default .glb by meaning.
     */
    @Transactional(readOnly = true)
    public List<SignMediaResponse> getSignMedia(List<String> meanings) {
        Map<String, String> requestedByLower = new LinkedHashMap<>();
        for (String meaning : meanings) {
            if (meaning != null && !meaning.isBlank()) {
                requestedByLower.putIfAbsent(meaning.strip().toLowerCase(Locale.ROOT), meaning);
            }
        }
        if (requestedByLower.isEmpty()) {
            return List.of();
        }
        List<SignMediaResponse> out = new ArrayList<>();
        for (Sign sign : signRepository.findByLowerMeaningIn(requestedByLower.keySet())) {
            String key = sign.getAnimationUrl();
            String format = SignMediaStorage.formatOf(key);
            String requested = requestedByLower.get(sign.getMeaning().toLowerCase(Locale.ROOT));
            if (format != null && requested != null) {
                out.add(
                        new SignMediaResponse(
                                requested,
                                signMediaStorage.publicUrl(key, sign.getAnimationUpdatedAt()),
                                format,
                                SignMediaStorage.MP4.equals(format)
                                                && sign.getLateralAnimationUrl() != null
                                        ? signMediaStorage.publicUrl(
                                                sign.getLateralAnimationUrl(),
                                                sign.getAnimationUpdatedAt())
                                        : null));
            }
        }
        return out;
    }
}
