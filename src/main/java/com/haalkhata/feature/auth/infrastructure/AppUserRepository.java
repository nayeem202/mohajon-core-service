package com.haalkhata.feature.auth.infrastructure;

import com.haalkhata.feature.auth.domain.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByPhone(String phone);

    Optional<AppUser> findByIdAndShopId(String id, String shopId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);
}
