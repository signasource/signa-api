package com.signasource.signa_api.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUse;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private JwtService jwtService;

	@InjectMocks
	private AuthService authService;

	private RegisterRequest registerRequest = new RegisterRequest("test@example.com", "password123", "Test User");
	private LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");
	private User testUser = User.builder().email("test@example.com").name("Test User")
			.passwordHash("hashed_password_123").role(Role.USER).build();
	private CustomUserDetails customUserDetails = new CustomUserDetails(testUser);

	@Test
	void testRegisterSuccess() {
		when(userRepository.existsByEmail(registerRequest.email())).thenReturn(false);
		when(passwordEncoder.encode(registerRequest.password())).thenReturn(testUser.getPasswordHash());

		authService.register(registerRequest);

		verify(userRepository).existsByEmail(registerRequest.email());
		verify(passwordEncoder).encode(registerRequest.password());
		verify(userRepository).save(any(User.class));
	}

	@Test
	void testRegisterWithDuplicateEmail() {
		when(userRepository.existsByEmail(registerRequest.email())).thenReturn(true);

		assertThrows(ResourceAlreadyInUse.class, () -> authService.register(registerRequest));

		verify(userRepository).existsByEmail(registerRequest.email());
		verify(passwordEncoder, never()).encode(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void testLoginSuccess() {
		String expectedToken = "jwt-token-123";
		Authentication authentication = new UsernamePasswordAuthenticationToken(customUserDetails, null,
				customUserDetails.getAuthorities());
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenReturn(authentication);
		when(jwtService.generateToken(customUserDetails)).thenReturn(expectedToken);

		String token = authService.login(loginRequest);

		assertNotNull(token);
		assertEquals(expectedToken, token);
		verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
		verify(jwtService).generateToken(customUserDetails);
	}

	@Test
	void testRegisterEncodesPassword() {
		when(userRepository.existsByEmail(registerRequest.email())).thenReturn(false);
		when(passwordEncoder.encode(registerRequest.password())).thenReturn(testUser.getPasswordHash());

		authService.register(registerRequest);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertEquals(testUser.getPasswordHash(), captor.getValue().getPasswordHash());
	}
}
