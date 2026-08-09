package com.haalkhata.feature.user.infrastructure;

import com.haalkhata.feature.user.domain.Menu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuRepository extends JpaRepository<Menu, String> {

    List<Menu> findAllByOrderBySortOrderAsc();

    @Query("SELECT m FROM Menu m WHERE m.code IN :codes")
    List<Menu> findByCodeIn(@Param("codes") List<String> codes);
}
