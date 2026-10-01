package com.haalkhata.feature.closing.infrastructure;

import com.haalkhata.feature.closing.domain.DailyCashClosing;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyCashClosingRepository extends JpaRepository<DailyCashClosing, String> {
    Optional<DailyCashClosing> findByShopIdAndBusinessDate(String shopId, LocalDate date);
    List<DailyCashClosing> findTop30ByShopIdOrderByBusinessDateDesc(String shopId);
    void deleteByShopId(String shopId);
}
