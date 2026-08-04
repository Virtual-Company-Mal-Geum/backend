package com.malgeum.geo.domain.domain.order.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import com.malgeum.geo.domain.domain.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
