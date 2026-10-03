package com.haalkhata.feature.sale.returning.infrastructure;

import com.haalkhata.feature.sale.returning.domain.SaleReturnItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleReturnItemRepository extends JpaRepository<SaleReturnItem, String> {
    List<SaleReturnItem> findByReturnId(String returnId);
    void deleteByReturnId(String returnId);
}
