package com.haalkhata.core.security;

import com.haalkhata.core.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService {

    private final AppProperties props;
    private final SecretKey key;
    private final SecretKey trustedKey;

    public JwtService(AppProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.trustedKey = props.getTrustedJwtSecret() == null || props.getTrustedJwtSecret().isBlank()
                ? null
                : Keys.hmacShaKeyFor(props.getTrustedJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(String userId, String email, String name, String shopId, String role, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(props.getAccessTokenMinutes()));
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("name", name)
                .claim("shopId", shopId)
                .claim("role", role)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) throws JwtException {
        try {
            return verifyWith(key, token);
        } catch (JwtException ex) {
            if (trustedKey != null) {
                return verifyWith(trustedKey, token);
            }
            throw ex;
        }
    }

    private Claims verifyWith(SecretKey secretKey, String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String generateRefreshToken() {
        return java.util.UUID.randomUUID() + "." + java.util.UUID.randomUUID();
    }
}
