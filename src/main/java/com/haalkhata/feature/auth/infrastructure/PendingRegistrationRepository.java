package com.haalkhata.feature.auth.infrastructure;

import com.haalkhata.feature.auth.domain.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, String> {
}
