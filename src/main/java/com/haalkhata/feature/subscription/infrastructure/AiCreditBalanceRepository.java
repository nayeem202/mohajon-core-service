package com.haalkhata.feature.subscription.infrastructure;

import com.haalkhata.feature.subscription.domain.AiCreditBalance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface AiCreditBalanceRepository extends JpaRepository<AiCreditBalance, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AiCreditBalance b where b.shopId = :shopId")
    Optional<AiCreditBalance> findByShopIdForUpdate(String shopId);
}
