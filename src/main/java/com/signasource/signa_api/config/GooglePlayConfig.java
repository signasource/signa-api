package com.signasource.signa_api.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GooglePlayProperties.class)
@ConditionalOnProperty(name = "google-play.enabled", havingValue = "true", matchIfMissing = true)
public class GooglePlayConfig {

    @Bean
    public AndroidPublisher androidPublisher(GooglePlayProperties props)
            throws IOException, GeneralSecurityException {
        byte[] decoded = Base64.getDecoder().decode(props.credentialsJson());
        GoogleCredentials credentials;
        try (ByteArrayInputStream serviceAccount = new ByteArrayInputStream(decoded)) {
            credentials =
                    GoogleCredentials.fromStream(serviceAccount)
                            .createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER);
        }
        HttpRequestInitializer initializer = new HttpCredentialsAdapter(credentials);
        return new AndroidPublisher.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        initializer)
                .setApplicationName("signa-api")
                .build();
    }
}
