package com.malgeum.geo.domain.pricing.packorder.repository;

import com.malgeum.geo.domain.pricing.packorder.entity.PackOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PackOrderRepository extends JpaRepository<PackOrder, Long> {
    Optional<PackOrder> findFirstByPricingAccount_IdAndStatusAndRemainingPagesGreaterThanAndExpiredAtAfterOrderByExpiredAtAscIdAsc(
            Long accountId, PackOrder.PackStatus status, int remainingPages, LocalDateTime now);
}
