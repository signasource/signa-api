package com.signasource.signa_api.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    @Value("${auth.jwt.secret}")
    private String secret;

    @Value("${auth.token-expirations.access}")
    private Long accessTokenExpiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserDetails user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isValid(String token, UserDetails user) {
        return extractUsername(token).equals(user.getUsername()) && !isExpired(token);
    }

    private boolean isExpired(String token) {
        Date expirationDate =
                Jwts.parser()
                        .verifyWith((javax.crypto.SecretKey) getSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
                        .getExpiration();

        return expirationDate.before(new Date());
    }
}
