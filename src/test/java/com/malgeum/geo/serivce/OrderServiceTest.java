package com.malgeum.geo.serivce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.malgeum.geo.service.OrderService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@DataJpaTest
@Import({ OrderService.class})
public class OrderServiceTest {
    @Test
    @DisplayName("Client의 Order이 제대로 입력되어 저장되는지 확인한다.")
    void createNewOrderAndSave() {
        String targetUrl = "https://biztoss.co.kr/";
    }

}
