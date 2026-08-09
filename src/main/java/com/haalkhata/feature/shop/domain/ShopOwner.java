package com.haalkhata.feature.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shop_owners")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopOwner {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "shop_id", nullable = false, length = 60)
    private String shopId;

    @Column(name = "owner_id", length = 36)
    private String ownerId;

    @Column(length = 150)
    private String name;

    @Column(name = "investment_amount", nullable = false)
    private double investmentAmount;
}
