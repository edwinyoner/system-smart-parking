package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad JPA de Zone (Oracle).
 *
 * El código es único dentro de cada parqueo (UK_ZONES_PARKING_CODE), no en todo el
 * sistema. La capacidad no se guarda: se cuenta desde SPACES.
 */
@Entity
@Table(name = "ZONES",
      uniqueConstraints = @UniqueConstraint(name = "UK_ZONES_PARKING_CODE", columnNames = {"PARKING_ID", "CODE"}),
      indexes = @Index(name = "IDX_ZONES_PARKING", columnList = "PARKING_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneEntity {

   @Id
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "zone_seq")
   @SequenceGenerator(name = "zone_seq", sequenceName = "SEQ_ZONE", allocationSize = 1)
   @Column(name = "ID")
   private Long id;

   @Column(name = "PARKING_ID", nullable = false)
   private Long parkingId;

   @Column(name = "NAME", nullable = false, length = 100)
   private String name;

   @Column(name = "CODE", nullable = false, length = 20)
   private String code;

   @Column(name = "ADDRESS", length = 200)
   private String address;

   @Column(name = "DESCRIPTION", length = 500)
   private String description;

   @Column(name = "LATITUDE")
   private Double latitude;

   @Column(name = "LONGITUDE")
   private Double longitude;

   @Column(name = "CAMERA_IDS", length = 500)
   private String cameraIds;

   @Column(name = "STATUS", nullable = false, length = 20)
   private String status;

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
      if (this.createdAt == null) this.createdAt = LocalDateTime.now();
      if (this.updatedAt == null) this.updatedAt = this.createdAt;
   }
}
