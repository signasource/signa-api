package com.signasource.signa_api.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.signasource.signa_api.users.entity.AuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.ChangePasswordRequest;
import com.signasource.signa_api.auth.dto.ForgotPasswordRequest;
import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.RefreshTokenRequest;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.dto.ResendVerificationEmailRequest;
import com.signasource.signa_api.auth.dto.ResetPasswordRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.entity.Token;
import com.signasource.signa_api.auth.entity.TokenType;
import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.InvalidCredentialsException;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.InvalidTokenException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final TokenRepository tokenRepository;
	private final EmailService emailService;
	private final GoogleIdTokenVerifier googleIdTokenVerifier;

	@Value("${auth.token-expirations.refresh}")
	private Long refreshTokenExpiration;

	@Value("${auth.token-expirations.password-reset}")
	private Long passwordResetTokenExpiration;

	@Value("${auth.token-expirations.email-verification}")
	private Long emailVerificationTokenExpiration;

	@Transactional
	public void register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new ResourceAlreadyInUseException("Email already in use");
		}

		User user = User.builder().email(request.email()).name(request.name())
				.passwordHash(passwordEncoder.encode(request.password())).role(Role.USER).enabled(false).build();

		userRepository.save(user);

		Token token = createToken(user, TokenType.EMAIL_VERIFICATION,
				Duration.ofMillis(emailVerificationTokenExpiration));

		emailService.sendVerificationEmail(user.getEmail(), token.getToken());
	}

	public AuthResponse login(LoginRequest request) {
		Authentication authentication = authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));

		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		User user = userDetails.getUser();

		if (!user.isEnabled()) {
			throw new InvalidCredentialsException("Account not verified");
		}

		return generateTokens(user);
	}

	public AuthResponse refreshToken(RefreshTokenRequest request) {
		Token refreshToken = getToken(request.refreshToken(), TokenType.REFRESH);
		validateExpiration(refreshToken);
		tokenRepository.delete(refreshToken);
		return generateTokens(refreshToken.getUser());
	}

	@Transactional
	public void verifyAccount(String token) {
		Token verificationToken = getToken(token, TokenType.EMAIL_VERIFICATION);
		validateExpiration(verificationToken);
		tokenRepository.delete(verificationToken);

		User user = verificationToken.getUser();
		user.setEnabled(true);

		userRepository.save(user);
	}

	@Transactional
	public AuthResponse changePassword(ChangePasswordRequest request) {
		User user = getAuthenticatedUser();

		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new InvalidInputException("Current password is incorrect");
		}

		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new InvalidInputException("New password must be different from current password");
		}

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);

		tokenRepository.deleteByUserAndType(user, TokenType.REFRESH);

		return generateTokens(user);
	}

	@Transactional
	public void forgotPassword(ForgotPasswordRequest request) {
		User user = userRepository.findByEmail(request.email()).orElse(null);

		if (user == null) {
			return;
		}

		tokenRepository.deleteByUserAndType(user, TokenType.PASSWORD_RESET);

		Token token = createToken(user, TokenType.PASSWORD_RESET, Duration.ofMillis(passwordResetTokenExpiration));
		emailService.sendPasswordResetEmail(user.getEmail(), token.getToken());
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest request, String token) {
		Token resetToken = getToken(token, TokenType.PASSWORD_RESET);
		validateExpiration(resetToken);

		User user = resetToken.getUser();
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new InvalidInputException("New password must be different from current password");
		}

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
		tokenRepository.delete(resetToken);
		tokenRepository.deleteByUserAndType(user, TokenType.REFRESH);
	}

	@Transactional
	public void resendVerificationEmail(ResendVerificationEmailRequest request) {
		User user = userRepository.findByEmail(request.email()).orElse(null);

		if (user == null) {
			return;
		}

		if (user.isEnabled()) {
			return;
		}

		tokenRepository.deleteByUserAndType(user, TokenType.EMAIL_VERIFICATION);

		Token token = createToken(user, TokenType.EMAIL_VERIFICATION,
				Duration.ofMillis(emailVerificationTokenExpiration));

		emailService.sendVerificationEmail(user.getEmail(), token.getToken());

	}

	@Transactional
	public AuthResponse authenticateWithGoogle(String idTokenString) {
		try {
			GoogleIdToken idToken = googleIdTokenVerifier.verify(idTokenString);

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
				boolean needsUpdate = false;

				if (!user.isEnabled()) {
					user.setEnabled(true);
					needsUpdate = true;
				}

				if (user.getProvider() != AuthProvider.GOOGLE) {
					user.setProvider(AuthProvider.GOOGLE);
					needsUpdate = true;
				}

				if (needsUpdate) {
					user = userRepository.save(user);
				}
			} else {
				user = userRepository.save(User.builder().email(email).name(name).passwordHash(null).role(Role.USER)
						.enabled(true).provider(AuthProvider.GOOGLE).build());
			}

			return generateTokens(user);

		} catch (Exception e) {
			throw new InvalidCredentialsException("Error authenticating with Google: " + e.getMessage());
		}
	}

	private AuthResponse generateTokens(User user) {
		CustomUserDetails userDetails = new CustomUserDetails(user);

		String accessToken = jwtService.generateToken(userDetails);
		Token refreshToken = createToken(user, TokenType.REFRESH, Duration.ofMillis(refreshTokenExpiration));

		return new AuthResponse(accessToken, refreshToken.getToken());
	}

	private Token getToken(String token, TokenType type) {
		return tokenRepository.findByTokenAndType(token, type).orElseThrow(() -> new InvalidTokenException());
	}

	private void validateExpiration(Token token) {
		if (token.getExpiryDate().isBefore(Instant.now())) {
			tokenRepository.delete(token);
			throw new InvalidTokenException();
		}
	}

	private User getAuthenticatedUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		return userDetails.getUser();
	}

	private Token createToken(User user, TokenType type, Duration duration) {
		Token token = Token.builder().token(UUID.randomUUID().toString()).user(user).type(type)
				.expiryDate(Instant.now().plus(duration)).build();

		return tokenRepository.save(token);
	}
}
