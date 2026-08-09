package com.haalkhata.feature.user.infrastructure;

import com.haalkhata.feature.user.domain.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {

    Optional<Role> findByCode(String code);
}
