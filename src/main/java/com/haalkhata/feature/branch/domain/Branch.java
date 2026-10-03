package com.haalkhata.feature.branch.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
@Entity @Table(name = "branches") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Branch {
    @Id @Column(name = "branch_id", length = 80) private String branchId;
    @Column(name = "shop_id", nullable = false, length = 60) private String shopId;
    @Column(nullable = false, length = 150) private String name;
    @Column(length = 255) private String address;
    @Column(length = 30) private String phone;
    @Column(name = "is_default", nullable = false) private boolean isDefault;
    @Column(name = "is_active", nullable = false) private boolean isActive;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
