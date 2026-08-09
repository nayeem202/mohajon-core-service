package com.haalkhata.feature.user.infrastructure;

import com.haalkhata.feature.user.domain.UserRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, String> {

    @Query("select r.code from UserRole ur join Role r on r.id = ur.roleId where ur.userId = :userId")
    List<String> findRoleCodesByUserId(@Param("userId") String userId);

    List<UserRole> findByUserId(String userId);

    void deleteByUserId(String userId);
}
