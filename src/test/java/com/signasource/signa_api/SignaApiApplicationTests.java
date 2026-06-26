package com.signasource.signa_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.google.firebase.messaging.FirebaseMessaging;

@SpringBootTest
@ActiveProfiles("test")
class SignaApiApplicationTests {

	@MockitoBean
	private FirebaseMessaging firebaseMessaging;

	@Test
	void contextLoads() {
	}
}
