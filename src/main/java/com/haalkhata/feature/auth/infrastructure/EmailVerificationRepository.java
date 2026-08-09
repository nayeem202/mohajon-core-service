package com.haalkhata.feature.auth.infrastructure;

import com.haalkhata.feature.auth.domain.EmailVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, String> {

    Optional<EmailVerification> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
