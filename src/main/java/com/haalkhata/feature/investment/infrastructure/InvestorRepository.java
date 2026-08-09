package com.haalkhata.feature.investment.infrastructure;

import com.haalkhata.feature.investment.domain.Investor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorRepository extends JpaRepository<Investor, String> {

    List<Investor> findByShopIdOrderByCreatedAtDesc(String shopId);

    Optional<Investor> findByInvestorIdAndShopId(String investorId, String shopId);

    void deleteByShopId(String shopId);
}
