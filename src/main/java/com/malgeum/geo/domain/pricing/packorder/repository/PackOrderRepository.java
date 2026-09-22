package com.malgeum.geo.domain.pricing.packorder.repository;

import com.malgeum.geo.domain.pricing.packorder.entity.PackOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackOrderRepository extends JpaRepository<PackOrder, Long> {
    public boolean existsByClientIdAndStatus(Long clientId, PackOrder.PackStatus status);
}
