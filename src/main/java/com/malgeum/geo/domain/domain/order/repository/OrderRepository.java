package com.malgeum.geo.domain.domain.order.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.malgeum.geo.domain.domain.order.entity.Order;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByClient_IdOrderByCreatedAtDesc(Long clientId);

    // 재평가 할때 동시성 충돌을 방지하기 위해 원 주문 데이터 행을 잠금으로 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findWithLockById(Long id);

    List<Order> findAllByParentOrder_IdOrderByCreatedAtAsc(Long parentOrderId);
}
