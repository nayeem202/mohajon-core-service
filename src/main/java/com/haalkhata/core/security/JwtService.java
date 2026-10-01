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
        var builder = Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("name", name)
                .claim("shopId", shopId)
                .claim("role", role)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key);
        if (props.getJwtIssuer() != null && !props.getJwtIssuer().isBlank()) {
            builder.issuer(props.getJwtIssuer()).audience().add(props.getJwtIssuer()).and();
        }
        return builder.compact();
    }

    public Claims parse(String token) throws JwtException {
        try {
            return verifySelf(token);
        } catch (JwtException ex) {
            // Tokens minted by the auth-service: shared-secret trust, no
            // issuer/audience requirement so rotation stays compatible.
            if (trustedKey != null) {
                return verifyWith(trustedKey, token, null);
            }
            throw ex;
        }
    }

    private Claims verifySelf(String token) {
        Claims claims = verifyWith(key, token, props.getJwtIssuer());
        var audiences = claims.getAudience();
        if (props.getJwtIssuer() != null && !props.getJwtIssuer().isBlank()
                && audiences != null && !audiences.isEmpty()
                && !audiences.contains(props.getJwtIssuer())) {
            throw new JwtException("Unexpected audience: " + audiences);
        }
        return claims;
    }

    private Claims verifyWith(SecretKey secretKey, String token, String expectedIssuer) {
        var builder = Jwts.parser()
                .verifyWith(secretKey);
        if (expectedIssuer != null && !expectedIssuer.isBlank()) {
            builder.requireIssuer(expectedIssuer);
        }
        return builder.build().parseSignedClaims(token).getPayload();
    }

    public String generateRefreshToken() {
        return java.util.UUID.randomUUID() + "." + java.util.UUID.randomUUID();
    }
}
