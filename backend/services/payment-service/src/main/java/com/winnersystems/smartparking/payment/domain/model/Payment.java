package com.winnersystems.smartparking.payment.domain.model;

import com.winnersystems.smartparking.payment.domain.exception.PaymentDomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pago de una estadía o de una multa.
 *
 * Reglas:
 * - El monto lo calcula parking-service (nunca lo envía el cliente) y se fija al crear el pago.
 * - Manual (efectivo): nace COMPLETED, con el operador que cobró y, si el tipo lo exige, el N.° de operación.
 * - Automático (QR): nace PENDING con el QR de la pasarela y vence en expiresAt; la pasarela
 *   lo confirma por webhook. Confirmar dos veces la misma operación no hace nada (idempotente).
 * - Solo un pago PENDING se cancela; solo uno pagado se devuelve, hasta el monto pagado.
 * - Es un registro histórico: no se elimina ni tiene soft delete.
 * - settlementNotified indica si parking-service ya sabe que se pagó (o se devolvió);
 *   si el aviso falla, una tarea lo reintenta.
 */
public class Payment {

   public static final String CURRENCY = "PEN";

   private Long id;
   private PaymentConcept concept;
   private Long sourceId;
   private String description;                // "Estadía ABC-123 · Parqueo San Martín" (para el comprobante)
   private String plateNumber;                // Placa, si la hay (estadías y multas con vehículo)
   private Long parkingId;                    // Parqueo de la estadía o la multa (copiado para los reportes)
   private String parkingName;

   // Tipo de pago: ID y copia de sus datos al momento de pagar (el tipo puede cambiar después)
   private Long paymentTypeId;
   private String paymentTypeCode;
   private String paymentTypeName;
   private ConfirmationMode confirmationMode;
   private PaymentChannel channel;

   private BigDecimal amount;
   private String currency = CURRENCY;
   private PaymentStatus status;

   // Pasarela (solo automáticos)
   private String gatewayReference;           // ID del cobro en la pasarela
   private String qrPayload;                  // Contenido del QR que muestra la pantalla, el kiosco o la app
   private LocalDateTime expiresAt;
   private String operationNumber;            // N.° de operación (de la pasarela o anotado por el operador)

   private Long operatorId;                   // Quién cobró (manual) o quién generó el QR
   private LocalDateTime paidAt;
   private String failureReason;
   private String cancelReason;

   // Devolución (manual)
   private BigDecimal refundedAmount = BigDecimal.ZERO;
   private String refundReason;
   private LocalDateTime refundedAt;
   private Long refundedBy;

   private boolean settlementNotified;

   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;

   public Payment() {
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
   }

   // ========================= CREACIÓN =========================

   /** Lo que se cobra, según parking-service */
   public record Charge(PaymentConcept concept, Long sourceId, BigDecimal amount, String description, String plateNumber,
                        Long parkingId, String parkingName) {

      public Charge(PaymentConcept concept, Long sourceId, BigDecimal amount, String description, String plateNumber) {
         this(concept, sourceId, amount, description, plateNumber, null, null);
      }
   }

   /** Pago en efectivo (u otro manual): el operador confirma al cobrar */
   public static Payment manual(Charge charge, PaymentType type, String operationNumber, Long operatorId) {
      if (type.isAutomatic()) {
         throw new PaymentDomainException("El tipo " + type.getCode() + " lo confirma la pasarela, no el operador");
      }
      String number = blankToNull(operationNumber);
      if (type.isRequiresReference() && number == null) {
         throw new PaymentDomainException("Anota el número de operación del pago con " + type.getName());
      }
      Payment p = base(charge, type, PaymentChannel.OPERATOR, operatorId);
      p.status = PaymentStatus.COMPLETED;
      p.operationNumber = number;
      p.paidAt = p.createdAt;
      return p;
   }

   /** Pago con QR: queda PENDING hasta que la pasarela lo confirme */
   public static Payment automatic(Charge charge, PaymentType type, PaymentChannel channel, Long requestedBy) {
      if (!type.isAutomatic()) {
         throw new PaymentDomainException("El tipo " + type.getCode() + " lo confirma un operador, no la pasarela");
      }
      Payment p = base(charge, type, channel, requestedBy);
      p.status = PaymentStatus.PENDING;
      return p;
   }

   private static Payment base(Charge charge, PaymentType type, PaymentChannel channel, Long userId) {
      if (charge == null || charge.concept() == null || charge.sourceId() == null) {
         throw new PaymentDomainException("Falta indicar qué se paga");
      }
      if (charge.amount() == null || charge.amount().signum() <= 0) {
         throw new PaymentDomainException("No hay monto por pagar");
      }
      if (channel == null || !type.isOfferedIn(channel)) {
         throw new PaymentDomainException("El tipo " + type.getName() + " no se ofrece en este canal");
      }
      Payment p = new Payment();
      p.concept = charge.concept();
      p.sourceId = charge.sourceId();
      p.description = blankToNull(charge.description());
      p.plateNumber = blankToNull(charge.plateNumber());
      p.parkingId = charge.parkingId();
      p.parkingName = blankToNull(charge.parkingName());
      p.amount = charge.amount().setScale(2, RoundingMode.HALF_UP);
      p.paymentTypeId = type.getId();
      p.paymentTypeCode = type.getCode();
      p.paymentTypeName = type.getName();
      p.confirmationMode = type.getConfirmationMode();
      p.channel = channel;
      p.operatorId = userId;
      p.createdBy = userId;
      p.updatedBy = userId;
      return p;
   }

   /** Datos del cobro creado en la pasarela */
   public void attachGatewayCharge(String gatewayReference, String qrPayload, LocalDateTime expiresAt) {
      requireStatus(PaymentStatus.PENDING, "adjuntar el QR");
      if (gatewayReference == null || gatewayReference.isBlank()) {
         throw new PaymentDomainException("La pasarela no devolvió la referencia del cobro");
      }
      this.gatewayReference = gatewayReference;
      this.qrPayload = qrPayload;
      this.expiresAt = expiresAt;
      touch(updatedBy);
   }

   // ========================= PASARELA =========================

   /**
    * La pasarela confirmó el pago.
    *
    * @return true si cambió el estado; false si ya estaba confirmado (aviso repetido)
    */
   public boolean confirm(String operationNumber, LocalDateTime paidAt) {
      if (status == PaymentStatus.COMPLETED) {
         return false;
      }
      requireStatus(PaymentStatus.PENDING, "confirmar");
      this.status = PaymentStatus.COMPLETED;
      this.operationNumber = blankToNull(operationNumber);
      this.paidAt = paidAt == null ? LocalDateTime.now() : paidAt;
      this.settlementNotified = false;
      touch(null);
      return true;
   }

   /** @return true si cambió el estado; false si ya estaba rechazado */
   public boolean fail(String reason) {
      if (status == PaymentStatus.FAILED) {
         return false;
      }
      requireStatus(PaymentStatus.PENDING, "rechazar");
      this.status = PaymentStatus.FAILED;
      this.failureReason = blankToNull(reason);
      touch(null);
      return true;
   }

   public boolean isExpiredAt(LocalDateTime now) {
      return status == PaymentStatus.PENDING && expiresAt != null && !now.isBefore(expiresAt);
   }

   /** Vence un QR sin pagar */
   public void expire(LocalDateTime now) {
      if (!isExpiredAt(now)) {
         throw new PaymentDomainException("El pago no está vencido");
      }
      this.status = PaymentStatus.EXPIRED;
      touch(null);
   }

   // ========================= OPERADOR =========================

   public void cancel(String reason, Long userId) {
      requireStatus(PaymentStatus.PENDING, "cancelar");
      String why = blankToNull(reason);
      if (why == null) {
         throw new PaymentDomainException("Indica el motivo de la cancelación");
      }
      this.status = PaymentStatus.CANCELLED;
      this.cancelReason = why;
      touch(userId);
   }

   /**
    * Devolución manual, total o parcial. Un pago se puede devolver en varias partes
    * hasta completar el monto pagado.
    *
    * @return true si con esta devolución el pago queda devuelto por completo
    */
   public boolean refund(BigDecimal refundAmount, String reason, Long userId) {
      if (!status.isPaid()) {
         throw new PaymentDomainException("Solo se devuelve un pago cobrado");
      }
      if (refundAmount == null || refundAmount.signum() <= 0) {
         throw new PaymentDomainException("El monto a devolver debe ser mayor que cero");
      }
      BigDecimal value = refundAmount.setScale(2, RoundingMode.HALF_UP);
      if (value.compareTo(getRefundableAmount()) > 0) {
         throw new PaymentDomainException("Solo se pueden devolver S/ " + getRefundableAmount());
      }
      String why = blankToNull(reason);
      if (why == null) {
         throw new PaymentDomainException("Indica el motivo de la devolución");
      }
      this.refundedAmount = refundedAmount.add(value);
      this.refundReason = why;
      this.refundedAt = LocalDateTime.now();
      this.refundedBy = userId;
      boolean full = refundedAmount.compareTo(amount) == 0;
      this.status = full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
      this.settlementNotified = false;
      touch(userId);
      return full;
   }

   public BigDecimal getRefundableAmount() {
      return status.isPaid() ? amount.subtract(refundedAmount) : BigDecimal.ZERO;
   }

   /** parking-service ya registró el último cambio (pago o devolución) */
   public void markSettlementNotified() {
      this.settlementNotified = true;
   }

   /** Hay algo que avisar a parking-service: se pagó o se devolvió y aún no lo sabe */
   public boolean needsSettlementNotice() {
      return !settlementNotified
            && (status.isPaid() || status == PaymentStatus.REFUNDED);
   }

   public boolean isAutomatic() {
      return confirmationMode == ConfirmationMode.AUTOMATIC;
   }

   // ========================= AUXILIARES =========================

   private void requireStatus(PaymentStatus expected, String action) {
      if (status != expected) {
         throw new PaymentDomainException("No se puede " + action + " un pago en estado " + status);
      }
   }

   private void touch(Long userId) {
      this.updatedAt = LocalDateTime.now();
      if (userId != null) {
         this.updatedBy = userId;
      }
   }

   private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
   }

   // ========================= GETTERS Y SETTERS =========================
   // Los setters los usa el mapper de persistencia; los cambios de estado van por los métodos de negocio

   public Long getId() { return id; }
   public void setId(Long id) { this.id = id; }
   public PaymentConcept getConcept() { return concept; }
   public void setConcept(PaymentConcept concept) { this.concept = concept; }
   public Long getSourceId() { return sourceId; }
   public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
   public String getDescription() { return description; }
   public void setDescription(String description) { this.description = description; }
   public String getPlateNumber() { return plateNumber; }
   public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
   public Long getParkingId() { return parkingId; }
   public void setParkingId(Long parkingId) { this.parkingId = parkingId; }
   public String getParkingName() { return parkingName; }
   public void setParkingName(String parkingName) { this.parkingName = parkingName; }
   public Long getPaymentTypeId() { return paymentTypeId; }
   public void setPaymentTypeId(Long paymentTypeId) { this.paymentTypeId = paymentTypeId; }
   public String getPaymentTypeCode() { return paymentTypeCode; }
   public void setPaymentTypeCode(String paymentTypeCode) { this.paymentTypeCode = paymentTypeCode; }
   public String getPaymentTypeName() { return paymentTypeName; }
   public void setPaymentTypeName(String paymentTypeName) { this.paymentTypeName = paymentTypeName; }
   public ConfirmationMode getConfirmationMode() { return confirmationMode; }
   public void setConfirmationMode(ConfirmationMode confirmationMode) { this.confirmationMode = confirmationMode; }
   public PaymentChannel getChannel() { return channel; }
   public void setChannel(PaymentChannel channel) { this.channel = channel; }
   public BigDecimal getAmount() { return amount; }
   public void setAmount(BigDecimal amount) { this.amount = amount; }
   public String getCurrency() { return currency; }
   public void setCurrency(String currency) { this.currency = currency; }
   public PaymentStatus getStatus() { return status; }
   public void setStatus(PaymentStatus status) { this.status = status; }
   public String getGatewayReference() { return gatewayReference; }
   public void setGatewayReference(String gatewayReference) { this.gatewayReference = gatewayReference; }
   public String getQrPayload() { return qrPayload; }
   public void setQrPayload(String qrPayload) { this.qrPayload = qrPayload; }
   public LocalDateTime getExpiresAt() { return expiresAt; }
   public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
   public String getOperationNumber() { return operationNumber; }
   public void setOperationNumber(String operationNumber) { this.operationNumber = operationNumber; }
   public Long getOperatorId() { return operatorId; }
   public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
   public LocalDateTime getPaidAt() { return paidAt; }
   public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
   public String getFailureReason() { return failureReason; }
   public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
   public String getCancelReason() { return cancelReason; }
   public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
   public BigDecimal getRefundedAmount() { return refundedAmount; }
   public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount == null ? BigDecimal.ZERO : refundedAmount; }
   public String getRefundReason() { return refundReason; }
   public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
   public LocalDateTime getRefundedAt() { return refundedAt; }
   public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
   public Long getRefundedBy() { return refundedBy; }
   public void setRefundedBy(Long refundedBy) { this.refundedBy = refundedBy; }
   public boolean isSettlementNotified() { return settlementNotified; }
   public void setSettlementNotified(boolean settlementNotified) { this.settlementNotified = settlementNotified; }
   public LocalDateTime getCreatedAt() { return createdAt; }
   public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
   public Long getCreatedBy() { return createdBy; }
   public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
   public LocalDateTime getUpdatedAt() { return updatedAt; }
   public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
   public Long getUpdatedBy() { return updatedBy; }
   public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

   /** Igualdad por ID: dos pagos nuevos (sin ID) no son iguales */
   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      return id != null && id.equals(((Payment) o).id);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(id);
   }

   @Override
   public String toString() {
      return "Payment{id=" + id + ", " + concept + "#" + sourceId + ", " + amount + " " + currency + ", " + status + '}';
   }
}
