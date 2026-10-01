package com.haalkhata.core.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityConfigValidator {

    private static final String[] DEV_SECRETS = {
            "haalkhata-dev-secret-change-me-in-production-0123456789abcdef",
            "dev-only-secret-change-me-in-production-0123456789",
    };

    private final AppProperties props;

    @PostConstruct
    void warnOnDevSecrets() {
        if (matchesDevSecret(props.getJwtSecret()) || matchesDevSecret(props.getTrustedJwtSecret())) {
            log.warn("""
                    ============================================================
                    SECURITY WARNING: The JWT secrets are still the development
                    defaults. Do NOT deploy to production without setting the
                    JWT_SECRET and TRUSTED_JWT_SECRET environment variables to
                    unique values, otherwise tokens are forgeable.
                    ============================================================""");
        }
    }

    private boolean matchesDevSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        for (String dev : DEV_SECRETS) {
            if (secret.equals(dev)) {
                return true;
            }
        }
        return false;
    }
}