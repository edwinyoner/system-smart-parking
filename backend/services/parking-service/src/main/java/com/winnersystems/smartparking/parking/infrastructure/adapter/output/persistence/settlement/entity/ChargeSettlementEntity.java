package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.settlement.entity;

import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tabla CHARGE_SETTLEMENTS: cuánto cubrió cada pago de payment-service (PAYMENT_ID único).
 * No guarda el tipo de pago ni la operación: eso vive en payment-service.
 */
@Entity
@Table(name = "CHARGE_SETTLEMENTS",
      uniqueConstraints = @UniqueConstraint(name = "UK_SETTLEMENT_PAYMENT", columnNames = "PAYMENT_ID"),
      indexes = @Index(name = "IX_SETTLEMENT_SOURCE", columnList = "CONCEPT, SOURCE_ID"))
@Getter
@Setter
@NoArgsConstructor
public class ChargeSettlementEntity {

   @Id
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "charge_settlement_seq")
   @SequenceGenerator(name = "charge_settlement_seq", sequenceName = "SEQ_CHARGE_SETTLEMENT", allocationSize = 1)
   @Column(name = "ID")
   private Long id;

   @Enumerated(EnumType.STRING)
   @Column(name = "CONCEPT", nullable = false, length = 20)
   private ChargeConcept concept;

   @Column(name = "SOURCE_ID", nullable = false)
   private Long sourceId;

   @Column(name = "PAYMENT_ID", nullable = false)
   private Long paymentId;

   @Column(name = "PAYMENT_STATUS", nullable = false, length = 20)
   private String paymentStatus;

   @Column(name = "AMOUNT", nullable = false, precision = 10, scale = 2)
   private BigDecimal amount;

   @Column(name = "REFUNDED_AMOUNT", nullable = false, precision = 10, scale = 2)
   private BigDecimal refundedAmount;

   @Column(name = "PAID_AT")
   private LocalDateTime paidAt;

   @Column(name = "CREATED_AT", nullable = false, updatable = false)
   private LocalDateTime createdAt;

   @Column(name = "UPDATED_AT", nullable = false)
   private LocalDateTime updatedAt;
}
