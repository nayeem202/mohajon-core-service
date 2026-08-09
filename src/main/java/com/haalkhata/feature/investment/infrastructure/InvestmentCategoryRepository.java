package com.haalkhata.feature.investment.infrastructure;

import com.haalkhata.feature.investment.domain.InvestmentCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentCategoryRepository extends JpaRepository<InvestmentCategory, String> {

    List<InvestmentCategory> findByShopIdOrderByCreatedAtDesc(String shopId);

    void deleteByShopId(String shopId);
}
