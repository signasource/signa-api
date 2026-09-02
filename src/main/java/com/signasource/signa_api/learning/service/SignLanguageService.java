package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.learning.dto.SignLanguageResponse;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignLanguageService {

    private final SignLanguageRepository signLanguageRepository;

    @Transactional(readOnly = true)
    public List<SignLanguageResponse> getAll() {
        return signLanguageRepository.findAll().stream().map(SignLanguageResponse::from).toList();
    }
}
