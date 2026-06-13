package com.signasource.signa_api.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.signasource.signa_api.auth.entity.Token;
import com.signasource.signa_api.auth.entity.TokenType;
import com.signasource.signa_api.users.entity.User;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {
	Optional<Token> findByTokenAndType(String token, TokenType type);

	void delete(Token token);

	void deleteByUserAndType(User user, TokenType type);
}
