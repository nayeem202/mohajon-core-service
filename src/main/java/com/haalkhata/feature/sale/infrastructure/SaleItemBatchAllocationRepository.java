package com.haalkhata.feature.sale.infrastructure;

import com.haalkhata.feature.sale.domain.SaleItemBatchAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemBatchAllocationRepository
        extends JpaRepository<SaleItemBatchAllocation, String> {
    List<SaleItemBatchAllocation> findBySaleId(String saleId);
    List<SaleItemBatchAllocation> findBySaleItemId(String saleItemId);
}
