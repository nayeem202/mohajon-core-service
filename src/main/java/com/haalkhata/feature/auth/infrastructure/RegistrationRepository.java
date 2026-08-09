package com.haalkhata.feature.auth.infrastructure;

import com.haalkhata.feature.auth.domain.Registration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, String> {

    Optional<Registration> findByUserId(String userId);
}
