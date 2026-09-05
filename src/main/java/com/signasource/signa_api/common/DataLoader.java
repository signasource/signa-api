package com.signasource.signa_api.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private static final String[] SEED_SCRIPTS = {
        "db/seed/achievements.sql", "db/seed/store_items.sql"
    };

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        for (String scriptPath : SEED_SCRIPTS) {
            loadScript(scriptPath);
        }
    }

    private void loadScript(String scriptPath) {
        try {
            String sql =
                    StreamUtils.copyToString(
                            new ClassPathResource(scriptPath).getInputStream(),
                            StandardCharsets.UTF_8);
            jdbcTemplate.execute(sql);
            log.info("Loaded seed script: {}", scriptPath);
        } catch (IOException e) {
            log.warn("Failed to read seed script: {} — check classpath", scriptPath, e);
        } catch (Exception e) {
            log.warn(
                    "Failed to execute seed script: {} — likely already exists (ON CONFLICT clause)",
                    scriptPath,
                    e);
        }
    }
}
