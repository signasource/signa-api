package com.signasource.signa_api.auth.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.RefreshTokenRequest;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.entity.RefreshToken;
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
	private final long refreshTokenDurationMs = 7 * 24 * 60 * 60 * 1000; // 7 days

	@Transactional
	public void register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new ResourceAlreadyInUse("Email already in use");
		}

		User user = User.builder().email(request.email()).name(request.name())
				.passwordHash(passwordEncoder.encode(request.password())).role(Role.USER).build();

		userRepository.save(user);
	}

	public AuthResponse login(LoginRequest request) {
		Authentication authentication = authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));

		CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

		String accessToken = jwtService.generateToken(user);
		RefreshToken refreshToken = createRefreshToken(user.getUser());

		return new AuthResponse(accessToken, refreshToken.getToken());
	}

	public AuthResponse refreshToken(RefreshTokenRequest request) {
		RefreshToken oldToken = validate(request.refreshToken());
		User user = oldToken.getUser();

		refreshTokenRepository.delete(oldToken);

		String newAccessToken = jwtService.generateToken(new CustomUserDetails(user));
		RefreshToken newRefreshToken = createRefreshToken(user);

		return new AuthResponse(newAccessToken, newRefreshToken.getToken());
	}

	private RefreshToken createRefreshToken(User user) {
		RefreshToken token = RefreshToken.builder().user(user).token(UUID.randomUUID().toString())
				.expiryDate(Instant.now().plusMillis(refreshTokenDurationMs)).build();
		return refreshTokenRepository.save(token);
	}

	private RefreshToken validate(String token) {
		RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
				.orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

		if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
			refreshTokenRepository.delete(refreshToken);
			throw new InvalidCredentialsException("Refresh token expired");
		}

		return refreshToken;
	}
}
