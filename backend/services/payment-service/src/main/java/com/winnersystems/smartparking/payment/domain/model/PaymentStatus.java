package com.winnersystems.smartparking.payment.domain.model;

/**
 * Estados de un pago.
 *
 * <pre>
 * PENDING ──► COMPLETED ──► PARTIALLY_REFUNDED ──► REFUNDED
 *    │            └────────────────────────────────► REFUNDED
 *    ├──► FAILED     (la pasarela lo rechazó)
 *    ├──► EXPIRED    (venció el QR sin pagarse)
 *    └──► CANCELLED  (se anuló antes de pagarse)
 * </pre>
 * Un pago manual (efectivo) nace COMPLETED: el operador confirma al cobrar.
 */
public enum PaymentStatus {
   PENDING,
   COMPLETED,
   FAILED,
   EXPIRED,
   CANCELLED,
   PARTIALLY_REFUNDED,
   REFUNDED;

   /** El dinero se recibió (aunque luego se haya devuelto una parte) */
   public boolean isPaid() {
      return this == COMPLETED || this == PARTIALLY_REFUNDED;
   }

   /** Ya no cambia por la pasarela */
   public boolean isClosed() {
      return this != PENDING;
   }
}
