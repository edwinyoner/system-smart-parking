package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.space.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad JPA de Space (Oracle).
 *
 * El código es único dentro de cada zona (UK_SPACES_ZONE_CODE), no en todo el sistema.
 * "Tiene sensor" no se guarda: se deduce de SENSOR_ID.
 */
@Entity
@Table(name = "SPACES",
      uniqueConstraints = @UniqueConstraint(name = "UK_SPACES_ZONE_CODE", columnNames = {"ZONE_ID", "CODE"}),
      indexes = {
            @Index(name = "IDX_SPACES_ZONE_STATUS", columnList = "ZONE_ID, STATUS"),
            @Index(name = "IDX_SPACES_SENSOR", columnList = "SENSOR_ID")
      })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceEntity {

   @Id
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "space_seq")
   @SequenceGenerator(name = "space_seq", sequenceName = "SEQ_SPACE", allocationSize = 1)
   @Column(name = "ID")
   private Long id;

   @Column(name = "ZONE_ID", nullable = false)
   private Long zoneId;

   @Column(name = "TYPE", nullable = false, length = 20)
   private String type;

   @Column(name = "CODE", nullable = false, length = 20)
   private String code;

   @Column(name = "DESCRIPTION", length = 255)
   private String description;

   @Column(name = "WIDTH")
   private Double width;

   @Column(name = "LENGTH")
   private Double length;

   @Column(name = "SENSOR_ID", length = 50)
   private String sensorId;

   @Column(name = "HAS_CAMERA_COVERAGE", nullable = false)
   private Boolean hasCameraCoverage;

   /** Última lectura del sensor: true = ve un vehículo; null = nunca informó */
   @Column(name = "SENSOR_OCCUPIED")
   private Boolean sensorOccupied;

   @Column(name = "SENSOR_READ_AT")
   private LocalDateTime sensorReadAt;

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
      if (this.hasCameraCoverage == null) this.hasCameraCoverage = false;
   }
}
