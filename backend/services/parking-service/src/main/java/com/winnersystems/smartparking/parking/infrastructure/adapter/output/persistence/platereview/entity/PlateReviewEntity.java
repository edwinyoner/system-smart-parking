package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.platereview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Tabla de la cola de revisión de placas. Es historial: no tiene borrado lógico.
 * CAPTURE_ID es único para que una captura reenviada no abra dos revisiones.
 */
@Entity
@Table(name = "PLATE_REVIEWS",
      uniqueConstraints = @UniqueConstraint(name = "UK_PLATE_REVIEWS_CAPTURE", columnNames = "CAPTURE_ID"),
      indexes = @Index(name = "IX_PLATE_REVIEWS_STATUS", columnList = "STATUS, PARKING_ID"))
@Getter
@Setter
@NoArgsConstructor
public class PlateReviewEntity {

   @Id
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "plate_review_seq")
   @SequenceGenerator(name = "plate_review_seq", sequenceName = "SEQ_PLATE_REVIEW", allocationSize = 1)
   @Column(name = "ID")
   private Long id;

   @Column(name = "CAPTURE_ID", nullable = false, length = 64)
   private String captureId;

   @Column(name = "DIRECTION", nullable = false, length = 10)
   private String direction;

   @Column(name = "PARKING_ID", nullable = false)
   private Long parkingId;

   @Column(name = "ZONE_ID")
   private Long zoneId;

   @Column(name = "DEVICE_ID", length = 64)
   private String deviceId;

   @Column(name = "DETECTED_PLATE", length = 20)
   private String detectedPlate;

   @Column(name = "CONFIDENCE", nullable = false)
   private Double confidence;

   @Column(name = "PHOTO_URL", length = 500)
   private String photoUrl;

   @Column(name = "STATUS", nullable = false, length = 20)
   private String status;

   @Column(name = "CONFIRMED_PLATE", length = 10)
   private String confirmedPlate;

   @Column(name = "TRANSACTION_ID")
   private Long transactionId;

   @Column(name = "DISCARD_REASON", length = 255)
   private String discardReason;

   @Column(name = "RESOLVED_BY")
   private Long resolvedBy;

   @Column(name = "RESOLVED_AT")
   private LocalDateTime resolvedAt;

   @Column(name = "CREATED_AT", nullable = false, updatable = false)
   private LocalDateTime createdAt;

   @Column(name = "UPDATED_AT", nullable = false)
   private LocalDateTime updatedAt;
}
