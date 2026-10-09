package com.haalkhata.feature.subscription.infrastructure;

import com.haalkhata.feature.subscription.domain.AiCreditUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCreditUsageRepository extends JpaRepository<AiCreditUsage, String> {}
