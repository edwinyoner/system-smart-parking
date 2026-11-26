package com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype.entity;

import com.winnersystems.smartparking.payment.domain.model.ConfirmationMode;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Tabla PAYMENT_TYPES (SQL Server). Los canales van en PAYMENT_TYPE_CHANNELS, una fila por canal.
 * La intercalación por defecto de SQL Server no distingue mayúsculas: el índice único de CODE
 * tampoco las distingue.
 */
@Entity
@Table(name = "PAYMENT_TYPES")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTypeEntity {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   @Column(name = "ID")
   private Long id;

   @Column(name = "CODE", nullable = false, unique = true, length = 20)
   private String code;

   @Column(name = "NAME", nullable = false, length = 100)
   private String name;

   @Column(name = "DESCRIPTION", length = 255)
   private String description;

   @Enumerated(EnumType.STRING)
   @Column(name = "CONFIRMATION_MODE", nullable = false, length = 20)
   private ConfirmationMode confirmationMode;

   @Column(name = "REQUIRES_REFERENCE", nullable = false)
   private Boolean requiresReference;

   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "PAYMENT_TYPE_CHANNELS", joinColumns = @JoinColumn(name = "PAYMENT_TYPE_ID"))
   @Enumerated(EnumType.STRING)
   @Column(name = "CHANNEL", nullable = false, length = 20)
   private Set<PaymentChannel> channels = new HashSet<>();

   @Column(name = "STATUS", nullable = false)
   private Boolean status;

   @Column(name = "CREATED_AT", nullable = false, updatable = false)
   private LocalDateTime createdAt;

   @Column(name = "CREATED_BY")
   private Long createdBy;

   @Column(name = "UPDATED_AT", nullable = false)
   private LocalDateTime updatedAt;

   @Column(name = "UPDATED_BY")
   private Long updatedBy;

   @Column(name = "DELETED_AT")
   private LocalDateTime deletedAt;

   @Column(name = "DELETED_BY")
   private Long deletedBy;

   @PrePersist
   protected void onCreate() {
      if (createdAt == null) createdAt = LocalDateTime.now();
      if (updatedAt == null) updatedAt = createdAt;
      if (status == null) status = false;
      if (requiresReference == null) requiresReference = false;
   }

   @PreUpdate
   protected void onUpdate() {
      updatedAt = LocalDateTime.now();
   }
}
