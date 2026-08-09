package com.haalkhata.feature.investment.infrastructure;

import com.haalkhata.feature.investment.domain.Investment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentRepository extends JpaRepository<Investment, String> {

    List<Investment> findByShopIdAndIsDeletedFalseOrderByCreatedAtDesc(String shopId);

    Optional<Investment> findByInvestmentIdAndShopIdAndIsDeletedFalse(String investmentId, String shopId);

    void deleteByShopId(String shopId);
}
