package com.signasource.signa_api.auth.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String email) {
		User user = userRepository.findByEmail(email).orElseThrow(() -> new NotFoundException("User not found"));
		return new CustomUserDetails(user);
	}
}
