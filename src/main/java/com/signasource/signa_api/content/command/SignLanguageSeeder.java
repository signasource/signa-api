package com.signasource.signa_api.content.command;

import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class SignLanguageSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SignLanguageSeeder.class);

    private final SignLanguageRepository signLanguageRepository;

    public SignLanguageSeeder(SignLanguageRepository signLanguageRepository) {
        this.signLanguageRepository = signLanguageRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensure("LSA", "Lengua de Señas Argentina", "ARG");
    }

    private void ensure(String code, String name, String countryCode) {
        if (signLanguageRepository.findByCode(code).isPresent()) {
            return;
        }
        signLanguageRepository.save(
                SignLanguage.builder().code(code).name(name).countryCode(countryCode).build());
        log.info("Seeded sign language {}", code);
    }
}
