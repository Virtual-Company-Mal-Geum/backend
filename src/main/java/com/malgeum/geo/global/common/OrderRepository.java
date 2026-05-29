package com.malgeum.geo.global.common;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malgeum.geo.domain.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByClient_IdOrderByCreatedAtDesc(Long clientId);
}
