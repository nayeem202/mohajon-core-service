package com.haalkhata.feature.shop.infrastructure;

import com.haalkhata.feature.shop.domain.Shop;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRepository extends JpaRepository<Shop, String> {

    Optional<Shop> findByOwnerUserId(String ownerUserId);
}
