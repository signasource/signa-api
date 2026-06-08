package com.signasource.signa_api.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.signasource.signa_api.auth.entity.PasswordResetToken;
import com.signasource.signa_api.users.entity.User;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
	Optional<PasswordResetToken> findByToken(String token);
	void deleteByUser(User user);
}
