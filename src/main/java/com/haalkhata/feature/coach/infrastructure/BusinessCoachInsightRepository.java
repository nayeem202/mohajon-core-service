package com.haalkhata.feature.coach.infrastructure;

import com.haalkhata.feature.coach.domain.BusinessCoachInsight;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessCoachInsightRepository extends JpaRepository<BusinessCoachInsight, String> {
    List<BusinessCoachInsight> findTop50ByShopIdOrderByCreatedAtDesc(String shopId);
    List<BusinessCoachInsight> findTop50ByShopIdAndBranchIdOrderByCreatedAtDesc(String shopId, String branchId);
    Optional<BusinessCoachInsight> findByShopIdAndInsightDateAndType(String shopId, LocalDate date, String type);
    Optional<BusinessCoachInsight> findByShopIdAndBranchIdAndInsightDateAndType(String shopId, String branchId, LocalDate date, String type);
    long countByShopIdAndInsightDate(String shopId, LocalDate date);
    long countByShopIdAndBranchIdAndInsightDate(String shopId, String branchId, LocalDate date);
    void deleteByShopId(String shopId);
}
