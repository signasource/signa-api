package com.signasource.signa_api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.firebase.messaging.FirebaseMessaging;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldAnswer401WhenTheBearerTokenIsMissing() throws Exception {
        mockMvc.perform(get("/organizations/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAnswer401WhenTheBearerTokenIsInvalid() throws Exception {
        mockMvc.perform(
                        get("/organizations/" + UUID.randomUUID() + "/overview")
                                .header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldKeepPublicRoutesOpen() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
