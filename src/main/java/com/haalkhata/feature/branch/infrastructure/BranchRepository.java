package com.haalkhata.feature.branch.infrastructure;
import com.haalkhata.feature.branch.domain.Branch;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BranchRepository extends JpaRepository<Branch, String> {
    List<Branch> findByShopIdOrderByIsDefaultDescNameAsc(String shopId);
    Optional<Branch> findByBranchIdAndShopId(String branchId, String shopId);
}
