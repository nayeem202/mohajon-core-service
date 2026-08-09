package com.haalkhata.feature.investment.infrastructure;

import com.haalkhata.feature.investment.domain.InvestmentMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentMemberRepository extends JpaRepository<InvestmentMember, String> {

    List<InvestmentMember> findByInvestmentIdOrderByCreatedAtAsc(String investmentId);

    List<InvestmentMember> findByInvestorIdOrderByCreatedAtAsc(String investorId);

    Optional<InvestmentMember> findFirstByInvestmentIdAndInvestorId(String investmentId, String investorId);

    void deleteByShopId(String shopId);
}
