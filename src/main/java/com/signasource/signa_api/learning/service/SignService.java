package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CreateSignRequest;
import com.signasource.signa_api.learning.dto.SignSummaryResponse;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
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
        SignLanguage signLanguage =
                signLanguageRepository
                        .findById(request.signLanguageId())
                        .orElseThrow(
                                () -> new NotFoundException("Sign language not found"));

        Sign sign =
                Sign.builder()
                        .meaning(request.meaning())
                        .description(request.description())
                        .handedness(request.handedness())
                        .animationUrl(request.animationUrl())
                        .signLanguage(signLanguage)
                        .build();

        Sign savedSign = signRepository.save(sign);
        return SignSummaryResponse.from(savedSign);
    }
}
