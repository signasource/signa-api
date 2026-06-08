package com.signasource.signa_api.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.ChangePasswordRequest;
import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.RefreshTokenRequest;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.entity.EmailVerificationToken;
import com.signasource.signa_api.auth.entity.RefreshToken;
import com.signasource.signa_api.auth.repository.EmailVerificationTokenRepository;
import com.signasource.signa_api.auth.repository.RefreshTokenRepository;
import com.signasource.signa_api.exceptions.InvalidCredentialsException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUse;
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
	private final RefreshTokenRepository refreshTokenRepository;
	private final EmailVerificationTokenRepository emailVerificationTokenRepository;
	private final EmailService emailService;

	@Value("${jwt.refresh-token-expiration}")
	private Long refreshTokenExpiration;

	@Transactional
	public void register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new ResourceAlreadyInUse("Email already in use");
		}

		User user = User.builder().email(request.email()).name(request.name())
				.passwordHash(passwordEncoder.encode(request.password())).role(Role.USER).enabled(false).build();

		userRepository.save(user);

		String token = UUID.randomUUID().toString();

		EmailVerificationToken verificationToken = EmailVerificationToken.builder().token(token).user(user)
				.expiryDate(Instant.now().plus(Duration.ofHours(24))).build();

		emailVerificationTokenRepository.save(verificationToken);
		emailService.sendVerificationEmail(user.getEmail(), token);
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
		RefreshToken oldToken = validateRefreshToken(request.refreshToken());

		refreshTokenRepository.delete(oldToken);

		return generateTokens(oldToken.getUser());
	}

	@Transactional
	public void verifyAccount(String token) {
		EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
				.orElseThrow(() -> new InvalidCredentialsException("Invalid token"));

		if (verificationToken.getExpiryDate().isBefore(Instant.now())) {
			emailVerificationTokenRepository.delete(verificationToken);
			throw new InvalidCredentialsException("Token expired");
		}

		User user = verificationToken.getUser();
		user.setEnabled(true);

		userRepository.save(user);
		emailVerificationTokenRepository.delete(verificationToken);
	}

	@Transactional
	public AuthResponse changePassword(ChangePasswordRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
		User user = userDetails.getUser();

		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new InvalidCredentialsException("Current password is incorrect");
		}

		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new InvalidCredentialsException("New password must be different from current password");
		}

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);

		refreshTokenRepository.deleteByUserId(user.getId());

		return generateTokens(user);
	}

	private AuthResponse generateTokens(User user) {
		CustomUserDetails userDetails = new CustomUserDetails(user);

		String accessToken = jwtService.generateToken(userDetails);
		RefreshToken refreshToken = createRefreshToken(user);

		return new AuthResponse(accessToken, refreshToken.getToken());
	}

	private RefreshToken createRefreshToken(User user) {
		RefreshToken token = RefreshToken.builder().user(user).token(UUID.randomUUID().toString())
				.expiryDate(Instant.now().plusMillis(refreshTokenExpiration)).build();
		return refreshTokenRepository.save(token);
	}

	private RefreshToken validateRefreshToken(String token) {
		RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
				.orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

		if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
			refreshTokenRepository.delete(refreshToken);
			throw new InvalidCredentialsException("Refresh token expired");
		}

		return refreshToken;
	}
}
