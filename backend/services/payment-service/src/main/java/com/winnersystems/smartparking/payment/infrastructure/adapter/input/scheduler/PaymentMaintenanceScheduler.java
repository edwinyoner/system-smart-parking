package com.winnersystems.smartparking.payment.infrastructure.adapter.input.scheduler;

import com.winnersystems.smartparking.payment.application.port.input.payment.PaymentMaintenanceUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada minuto: vence los QR sin pagar y reintenta los avisos a parking-service que fallaron */
@Slf4j
@Component
public class PaymentMaintenanceScheduler {

   private final PaymentMaintenanceUseCase useCase;

   public PaymentMaintenanceScheduler(PaymentMaintenanceUseCase useCase) {
      this.useCase = useCase;
   }

   @Scheduled(fixedDelayString = "${payment.maintenance.interval-ms:60000}", initialDelay = 30000)
   public void run() {
      try {
         useCase.expirePendingPayments();
         int delivered = useCase.retrySettlementNotices();
         if (delivered > 0) {
            log.info("{} aviso(s) pendientes entregados a parking-service", delivered);
         }
      } catch (RuntimeException e) {
         // Una falla no detiene la tarea: se intenta otra vez en el siguiente ciclo
         log.warn("Mantenimiento de pagos con error: {}", e.getMessage());
      }
   }
}
