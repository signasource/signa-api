package com.signasource.signa_api.content.command;

import com.signasource.signa_api.learning.entity.Handedness;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Local-only seeder that guarantees one Sign points at the R2 demo object {@code lsa/test.glb}, so
 * the animation PoC has a real Sign id to query without hardcoding a UUID. Idempotent: it reuses an
 * existing demo Sign if present and logs the id to wire into the mobile app.
 */
@Component
@Profile("local")
@Order(2)
public class TestSignSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestSignSeeder.class);

    private static final String DEMO_MEANING = "test";
    private static final String DEMO_ANIMATION_KEY = "lsa/test.glb";
    private static final String LSA_CODE = "LSA";

    private final SignRepository signRepository;
    private final SignLanguageRepository signLanguageRepository;

    public TestSignSeeder(
            SignRepository signRepository, SignLanguageRepository signLanguageRepository) {
        this.signRepository = signRepository;
        this.signLanguageRepository = signLanguageRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<Sign> existing = signRepository.findByMeaning(DEMO_MEANING);
        if (existing.isPresent()) {
            log.info(
                    "Animation PoC demo sign meaning='{}' id={} (animationUrl={})",
                    DEMO_MEANING,
                    existing.get().getId(),
                    existing.get().getAnimationUrl());
            return;
        }

        Optional<SignLanguage> lsa = signLanguageRepository.findByCode(LSA_CODE);
        if (lsa.isEmpty()) {
            log.warn("Cannot seed animation PoC demo sign: sign language {} not found", LSA_CODE);
            return;
        }

        Sign sign =
                signRepository.save(
                        Sign.builder()
                                .meaning(DEMO_MEANING)
                                .handedness(Handedness.ONE_HANDED)
                                .animationUrl(DEMO_ANIMATION_KEY)
                                .signLanguage(lsa.get())
                                .build());

        log.info(
                "Seeded animation PoC demo sign meaning='{}' id={} (animationUrl={})",
                DEMO_MEANING,
                sign.getId(),
                DEMO_ANIMATION_KEY);
    }
}
