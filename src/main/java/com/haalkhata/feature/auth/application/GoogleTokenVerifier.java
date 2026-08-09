package com.haalkhata.feature.auth.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GoogleTokenVerifier {

    private static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final ConcurrentMap<String, java.security.Key> CACHE = new ConcurrentHashMap<>();

    private final AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public GoogleTokenVerifier(AppProperties props) {
        this.props = props;
    }

    public Claims verify(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw BusinessException.unauthorized("Invalid Google ID token");
            }
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode payload = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            String kid = header.get("kid").asText();
            String alg = header.get("alg").asText();

            PublicKey key = (PublicKey) resolveKey(kid, alg);
            Claims claims = Jwts.parser().verifyWith(key)
                    .build().parseSignedClaims(idToken).getPayload();

            String aud = claims.get("aud", String.class);
            String clientId = props.getGoogleClientId();
            if (!clientId.isBlank() && !clientId.equals(aud)) {
                throw BusinessException.unauthorized("Google ID token audience mismatch");
            }
            return claims;
        } catch (BusinessException ex) {
            throw ex;
        } catch (SignatureException ex) {
            log.warn("Google token signature verification failed: {}", ex.getMessage());
            throw BusinessException.unauthorized("Google token verification failed");
        } catch (JwtException | java.io.IOException | InterruptedException ex) {
            log.warn("Google token verification error: {}", ex.getMessage());
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw BusinessException.unauthorized("Google token verification failed");
        }
    }

    private java.security.Key resolveKey(String kid, String alg) throws java.io.IOException, InterruptedException {
        java.security.Key cached = CACHE.get(kid);
        if (cached != null) {
            return cached;
        }
        refreshKeys();
        java.security.Key key = CACHE.get(kid);
        if (key == null) {
            throw BusinessException.unauthorized("Unknown Google signing key: " + kid);
        }
        return key;
    }

    private void refreshKeys() throws java.io.IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(JWKS_URL))
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw BusinessException.unauthorized("Could not fetch Google signing keys");
        }
        JsonNode root = mapper.readTree(response.body());
        for (JsonNode keyNode : root.path("keys")) {
            String kid = keyNode.get("kid").asText();
            String alg = keyNode.get("alg").asText();
            if ("RS256".equals(alg)) {
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(keyNode.get("n").asText()));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(keyNode.get("e").asText()));
                try {
                    PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
                    CACHE.put(kid, key);
                } catch (Exception ex) {
                    log.warn("Failed to build Google public key {}: {}", kid, ex.getMessage());
                }
            } else if ("HS256".equals(alg)) {
                byte[] secret = Base64.getUrlDecoder().decode(keyNode.get("k").asText());
                CACHE.put(kid, new SecretKeySpec(secret, "HmacSHA256"));
            }
        }
        log.info("Refreshed {} Google signing keys", CACHE.size());
    }
}
