package com.vnpay.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.vnpay.platform.mapper")
@EnableScheduling
public class VnPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(VnPaymentApplication.class, args);
    }
}
