package com.malgeum.geo.global.common;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malgeum.geo.domain.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
