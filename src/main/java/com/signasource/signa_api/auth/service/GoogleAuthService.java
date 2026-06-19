package com.signasource.signa_api.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.entity.Token;
import com.signasource.signa_api.auth.entity.TokenType;
import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.InvalidCredentialsException;
import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final TokenRepository tokenRepository;
	private final GoogleIdTokenVerifier verifier;

	@Value("${auth.token-expirations.refresh}")
	private Long refreshTokenExpiration;

	@Transactional
	public AuthResponse authenticateWithGoogle(String idTokenString) {
		try {
			GoogleIdToken idToken = verifier.verify(idTokenString);

			if (idToken == null) {
				throw new InvalidCredentialsException("Token de Google inválido");
			}

			GoogleIdToken.Payload payload = idToken.getPayload();
			String email = payload.getEmail();
			String name = (String) payload.get("name");

			Optional<User> userOptional = userRepository.findByEmail(email);
			User user;

			if (userOptional.isPresent()) {
				user = userOptional.get();
			} else {
				user = User.builder().email(email).name(name).passwordHash(null).role(Role.USER).enabled(true)
						.provider(AuthProvider.GOOGLE).build();
				userRepository.save(user);
			}

			return generateTokens(user);

		} catch (Exception e) {
			throw new InvalidCredentialsException("Error authenticating with Google: " + e.getMessage());
		}
	}

	private AuthResponse generateTokens(User user) {
		CustomUserDetails userDetails = new CustomUserDetails(user);

		String accessToken = jwtService.generateToken(userDetails);

		Token refreshToken = Token.builder().token(UUID.randomUUID().toString()).user(user).type(TokenType.REFRESH)
				.expiryDate(Instant.now().plus(Duration.ofMillis(refreshTokenExpiration))).build();

		tokenRepository.save(refreshToken);

		return new AuthResponse(accessToken, refreshToken.getToken());
	}
}
