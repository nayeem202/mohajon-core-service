package com.haalkhata.feature.auth.infrastructure;

import com.haalkhata.feature.auth.domain.OtpCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpCodeRepository extends JpaRepository<OtpCode, String> {

    Optional<OtpCode> findTopByPhoneOrderByCreatedAtDesc(String phone);
}
