package com.haalkhata.feature.user.infrastructure;

import com.haalkhata.feature.user.domain.UserMenu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserMenuRepository extends JpaRepository<UserMenu, String> {

    List<UserMenu> findByUserId(String userId);

    @Query("SELECT m.code FROM UserMenu um JOIN Menu m ON m.id = um.menuId WHERE um.userId = :userId")
    List<String> findMenuCodesByUserId(@Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM UserMenu um WHERE um.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
