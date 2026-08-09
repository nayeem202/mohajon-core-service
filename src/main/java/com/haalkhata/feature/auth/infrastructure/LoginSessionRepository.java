package com.haalkhata.feature.auth.infrastructure;

import com.haalkhata.feature.auth.domain.LoginSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionRepository extends JpaRepository<LoginSession, String> {

    List<LoginSession> findByUserIdOrderByLastLoginAtDesc(String userId);

    Optional<LoginSession> findByUserIdAndDeviceId(String userId, String deviceId);

    void deleteByUserId(String userId);
}
