package com.haalkhata.feature.sale.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sale_item_batch_allocations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleItemBatchAllocation {
    @Id
    @Column(name = "allocation_id", length = 36)
    private String allocationId;
    @Column(name = "sale_id", nullable = false, length = 36)
    private String saleId;
    @Column(name = "sale_item_id", nullable = false, length = 36)
    private String saleItemId;
    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
