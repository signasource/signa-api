package com.signasource.signa_api.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
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

	@Transactional
	public void register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new ResourceAlreadyInUse("Email already in use");
		}

		User user = User.builder().email(request.email()).name(request.name())
				.passwordHash(passwordEncoder.encode(request.password())).role(Role.USER).build();

		userRepository.save(user);
	}

	public String login(LoginRequest request) {
		Authentication authentication = authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));

		CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
		return jwtService.generateToken(user);
	}
}
