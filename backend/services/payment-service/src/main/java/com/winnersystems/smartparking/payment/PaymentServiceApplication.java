package com.winnersystems.smartparking.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Microservicio de pagos: tipos de pago, cobros y comprobantes internos.
 *
 * parking-service sabe cuánto se debe (tarifa y tiempo); payment-service sabe si se pagó.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class PaymentServiceApplication {

   public static void main(String[] args) {
      SpringApplication.run(PaymentServiceApplication.class, args);
   }
}
