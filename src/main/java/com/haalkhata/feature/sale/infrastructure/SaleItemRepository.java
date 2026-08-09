package com.haalkhata.feature.sale.infrastructure;

import com.haalkhata.feature.sale.domain.SaleItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, String> {

    List<SaleItem> findBySaleId(String saleId);

    void deleteBySaleId(String saleId);

}
