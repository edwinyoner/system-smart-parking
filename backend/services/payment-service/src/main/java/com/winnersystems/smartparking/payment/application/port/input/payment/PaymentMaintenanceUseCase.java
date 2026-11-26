package com.winnersystems.smartparking.payment.application.port.input.payment;

/** Tareas periódicas */
public interface PaymentMaintenanceUseCase {

   /** Vence los QR sin pagar. @return cuántos vencieron */
   int expirePendingPayments();

   /** Reintenta los avisos a parking-service que fallaron. @return cuántos se entregaron */
   int retrySettlementNotices();
}
