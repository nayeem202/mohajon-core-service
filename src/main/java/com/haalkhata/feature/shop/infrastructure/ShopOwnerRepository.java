package com.haalkhata.feature.shop.infrastructure;

import com.haalkhata.feature.shop.domain.ShopOwner;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopOwnerRepository extends JpaRepository<ShopOwner, String> {

    List<ShopOwner> findByShopId(String shopId);

    void deleteByShopId(String shopId);
}
