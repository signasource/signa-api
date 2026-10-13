package com.signasource.signa_api.admin.service;

import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.email:}")
    private String seedEmail;

    @Value("${admin.seed.password:}")
    private String seedPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (seedEmail == null
                || seedEmail.isBlank()
                || seedPassword == null
                || seedPassword.isBlank()) {
            return;
        }
        if (userRepository.existsByEmail(seedEmail)) {
            log.info("Seed admin already exists: {}", seedEmail);
            return;
        }
        String base =
                seedEmail
                        .substring(0, seedEmail.indexOf('@'))
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]", "");
        if (base.length() > 20) base = base.substring(0, 20);
        if (base.isEmpty()) base = "admin";
        String username = base;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + suffix++;
        }
        User admin =
                User.builder()
                        .email(seedEmail)
                        .username(username)
                        .passwordHash(passwordEncoder.encode(seedPassword))
                        .name("Admin")
                        .lastName("Signa")
                        .role(Role.ADMIN)
                        .enabled(true)
                        .verified(true)
                        .providers(Set.of(AuthProvider.LOCAL))
                        .build();
        userRepository.save(admin);
        userSettingsRepository.save(UserSettings.builder().user(admin).build());
        log.info("Seed admin created: {}", seedEmail);
    }
}
