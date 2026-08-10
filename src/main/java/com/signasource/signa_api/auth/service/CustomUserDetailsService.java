package com.signasource.signa_api.auth.service;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) {
        User user =
                (identifier.contains("@")
                                ? userRepository.findByEmail(identifier)
                                : userRepository.findByUsername(identifier))
                        .orElseThrow(() -> new NotFoundException("User not found"));
        return new CustomUserDetails(user);
    }
}
