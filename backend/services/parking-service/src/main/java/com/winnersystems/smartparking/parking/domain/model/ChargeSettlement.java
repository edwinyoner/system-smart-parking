package com.winnersystems.smartparking.parking.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Lo que payment-service confirmó sobre un pago de una estadía o una multa.
 *
 * No es el pago: el pago (tipo, QR, N.° de operación, devoluciones) vive solo en payment-service.
 * parking-service guarda solo cuánto cubrió cada pago, para:
 * - saber cuánto falta pagar (suma de netAmount) y permitir la salida;
 * - registrar cada aviso una sola vez: los avisos se reintentan y pueden repetirse.
 *
 * Hay una fila por pago (paymentId único); un aviso nuevo del mismo pago (por ejemplo, una
 * devolución) actualiza su estado y su monto neto.
 */
public class ChargeSettlement {

   private Long id;
   private ChargeConcept concept;
   private Long sourceId;
   private Long paymentId;
   private String paymentStatus;          // Estado en payment-service: COMPLETED, PARTIALLY_REFUNDED, REFUNDED
   private BigDecimal amount;             // Monto cobrado
   private BigDecimal refundedAmount;     // Devuelto hasta ahora
   private LocalDateTime paidAt;
   private LocalDateTime createdAt;
   private LocalDateTime updatedAt;

   public ChargeSettlement() {
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
      this.refundedAmount = BigDecimal.ZERO;
   }

   public static ChargeSettlement of(ChargeConcept concept, Long sourceId, Long paymentId) {
      if (concept == null || sourceId == null || paymentId == null) {
         throw new IllegalArgumentException("Faltan el concepto, el origen o el ID del pago");
      }
      ChargeSettlement s = new ChargeSettlement();
      s.concept = concept;
      s.sourceId = sourceId;
      s.paymentId = paymentId;
      return s;
   }

   /**
    * Aplica el estado que avisa payment-service.
    *
    * @return false si ya estaba registrado igual (aviso repetido)
    */
   public boolean update(String status, BigDecimal amount, BigDecimal refundedAmount, LocalDateTime paidAt) {
      BigDecimal refunded = refundedAmount == null ? BigDecimal.ZERO : refundedAmount;
      if (amount == null || amount.signum() < 0 || refunded.signum() < 0 || refunded.compareTo(amount) > 0) {
         throw new IllegalArgumentException("Montos del pago inválidos");
      }
      boolean same = Objects.equals(this.paymentStatus, status)
            && this.amount != null && this.amount.compareTo(amount) == 0
            && this.refundedAmount.compareTo(refunded) == 0;
      if (same) {
         return false;
      }
      this.paymentStatus = status;
      this.amount = amount;
      this.refundedAmount = refunded;
      this.paidAt = paidAt;
      this.updatedAt = LocalDateTime.now();
      return true;
   }

   /** Lo que este pago cubre hoy: cobrado menos devuelto */
   public BigDecimal getNetAmount() {
      return amount == null ? BigDecimal.ZERO : amount.subtract(refundedAmount);
   }

   public Long getId() { return id; }
   public void setId(Long id) { this.id = id; }
   public ChargeConcept getConcept() { return concept; }
   public void setConcept(ChargeConcept concept) { this.concept = concept; }
   public Long getSourceId() { return sourceId; }
   public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
   public Long getPaymentId() { return paymentId; }
   public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
   public String getPaymentStatus() { return paymentStatus; }
   public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
   public BigDecimal getAmount() { return amount; }
   public void setAmount(BigDecimal amount) { this.amount = amount; }
   public BigDecimal getRefundedAmount() { return refundedAmount; }
   public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount == null ? BigDecimal.ZERO : refundedAmount; }
   public LocalDateTime getPaidAt() { return paidAt; }
   public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
   public LocalDateTime getCreatedAt() { return createdAt; }
   public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
   public LocalDateTime getUpdatedAt() { return updatedAt; }
   public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

   /** Igualdad por ID del pago: hay un solo registro por pago */
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      return paymentId != null && paymentId.equals(((ChargeSettlement) o).paymentId);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(paymentId);
   }
}
