package com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.payment.entity;

import com.winnersystems.smartparking.payment.domain.model.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tabla PAYMENTS (SQL Server). Registro histórico: sin soft delete.
 * Dos avisos simultáneos de la pasarela dejan el mismo estado; parking-service registra cada pago una sola vez.
 */
@Entity
@Table(name = "PAYMENTS", indexes = {
      @Index(name = "IX_PAYMENTS_SOURCE", columnList = "CONCEPT, SOURCE_ID"),
      @Index(name = "IX_PAYMENTS_GATEWAY_REF", columnList = "GATEWAY_REFERENCE"),
      @Index(name = "IX_PAYMENTS_STATUS", columnList = "STATUS, EXPIRES_AT"),
      @Index(name = "IX_PAYMENTS_TYPE", columnList = "PAYMENT_TYPE_ID"),
      @Index(name = "IX_PAYMENTS_PAID_AT", columnList = "STATUS, PAID_AT")
})
@Getter
@Setter
@NoArgsConstructor
public class PaymentEntity {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   @Column(name = "ID")
   private Long id;

   @Enumerated(EnumType.STRING)
   @Column(name = "CONCEPT", nullable = false, length = 20)
   private PaymentConcept concept;

   @Column(name = "SOURCE_ID", nullable = false)
   private Long sourceId;

   @Column(name = "DESCRIPTION", length = 255)
   private String description;

   @Column(name = "PLATE_NUMBER", length = 15)
   private String plateNumber;

   @Column(name = "PARKING_ID")
   private Long parkingId;

   @Column(name = "PARKING_NAME", length = 100)
   private String parkingName;

   @Column(name = "PAYMENT_TYPE_ID", nullable = false)
   private Long paymentTypeId;

   @Column(name = "PAYMENT_TYPE_CODE", nullable = false, length = 20)
   private String paymentTypeCode;

   @Column(name = "PAYMENT_TYPE_NAME", nullable = false, length = 100)
   private String paymentTypeName;

   @Enumerated(EnumType.STRING)
   @Column(name = "CONFIRMATION_MODE", nullable = false, length = 20)
   private ConfirmationMode confirmationMode;

   @Enumerated(EnumType.STRING)
   @Column(name = "CHANNEL", nullable = false, length = 20)
   private PaymentChannel channel;

   @Column(name = "AMOUNT", nullable = false, precision = 10, scale = 2)
   private BigDecimal amount;

   @Column(name = "CURRENCY", nullable = false, length = 3)
   private String currency;

   @Enumerated(EnumType.STRING)
   @Column(name = "STATUS", nullable = false, length = 20)
   private PaymentStatus status;

   @Column(name = "GATEWAY_REFERENCE", length = 64)
   private String gatewayReference;

   @Column(name = "QR_PAYLOAD", length = 512)
   private String qrPayload;

   @Column(name = "EXPIRES_AT")
   private LocalDateTime expiresAt;

   @Column(name = "OPERATION_NUMBER", length = 64)
   private String operationNumber;

   @Column(name = "OPERATOR_ID")
   private Long operatorId;

   @Column(name = "PAID_AT")
   private LocalDateTime paidAt;

   @Column(name = "FAILURE_REASON", length = 255)
   private String failureReason;

   @Column(name = "CANCEL_REASON", length = 255)
   private String cancelReason;

   @Column(name = "REFUNDED_AMOUNT", nullable = false, precision = 10, scale = 2)
   private BigDecimal refundedAmount;

   @Column(name = "REFUND_REASON", length = 255)
   private String refundReason;

   @Column(name = "REFUNDED_AT")
   private LocalDateTime refundedAt;

   @Column(name = "REFUNDED_BY")
   private Long refundedBy;

   @Column(name = "SETTLEMENT_NOTIFIED", nullable = false)
   private Boolean settlementNotified;

   @Column(name = "CREATED_AT", nullable = false, updatable = false)
   private LocalDateTime createdAt;

   @Column(name = "CREATED_BY")
   private Long createdBy;

   @Column(name = "UPDATED_AT", nullable = false)
   private LocalDateTime updatedAt;

   @Column(name = "UPDATED_BY")
   private Long updatedBy;

   @PrePersist
   protected void onCreate() {
      if (createdAt == null) createdAt = LocalDateTime.now();
      if (updatedAt == null) updatedAt = createdAt;
      if (refundedAmount == null) refundedAmount = BigDecimal.ZERO;
      if (settlementNotified == null) settlementNotified = false;
   }

   @PreUpdate
   protected void onUpdate() {
      updatedAt = LocalDateTime.now();
   }
}
