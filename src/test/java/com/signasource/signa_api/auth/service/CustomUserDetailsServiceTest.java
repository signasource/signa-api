package com.signasource.signa_api.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService Tests")
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private CustomUserDetailsService customUserDetailsService;

    private String testEmail = "test@example.com";
    private User testUser =
            User.builder()
                    .id(UUID.randomUUID())
                    .email(testEmail)
                    .name("Test User")
                    .passwordHash("hashed_password_123")
                    .role(Role.USER)
                    .build();

    @Test
    void testLoadUserByUsernameSuccess() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(testEmail);

        assertNotNull(userDetails);
        assertEquals(testEmail, userDetails.getUsername());
        verify(userRepository).findByEmail(testEmail);
    }

    @Test
    void testLoadUserByUsernameNotFound() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername(testEmail));

        verify(userRepository).findByEmail(testEmail);
    }

    @Test
    void testLoadUserByUsernameNotFoundMessage() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        NotFoundException exception =
                assertThrows(
                        NotFoundException.class,
                        () -> customUserDetailsService.loadUserByUsername(testEmail));

        assertEquals("User not found", exception.getMessage());
    }
}
