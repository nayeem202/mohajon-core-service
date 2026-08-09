package com.haalkhata.feature.payment.infrastructure;

import com.haalkhata.feature.payment.domain.PaymentMethod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, String> {

    List<PaymentMethod> findByEnabledTrueOrderBySortOrderAsc();

    List<PaymentMethod> findAllByOrderBySortOrderAsc();

}
