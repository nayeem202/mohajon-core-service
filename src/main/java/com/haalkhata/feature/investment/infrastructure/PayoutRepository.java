package com.haalkhata.feature.investment.infrastructure;

import com.haalkhata.feature.investment.domain.Payout;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<Payout, String> {

    List<Payout> findByInvestmentIdOrderByCreatedAtDesc(String investmentId);

    List<Payout> findByInvestorIdOrderByCreatedAtDesc(String investorId);

    void deleteByShopId(String shopId);
}
