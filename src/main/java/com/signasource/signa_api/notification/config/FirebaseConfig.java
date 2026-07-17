package com.signasource.signa_api.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseConfig {

    @Value("${firebase.credentials-json}")
    private String credentialsJsonBase64;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        byte[] decoded = Base64.getDecoder().decode(credentialsJsonBase64);
        try (ByteArrayInputStream serviceAccount = new ByteArrayInputStream(decoded)) {
            FirebaseOptions options =
                    FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();
            return FirebaseApp.initializeApp(options);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
