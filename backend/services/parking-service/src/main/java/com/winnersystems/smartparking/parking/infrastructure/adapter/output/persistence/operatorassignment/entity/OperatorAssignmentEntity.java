package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.operatorassignment.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Asignación de un operador a un parqueo (o zona) en un turno.
 * OPERATOR_ID apunta a auth-service (sin FK en la base); ZONE_ID nulo = todo el parqueo.
 */
@Entity
@Table(name = "OPERATOR_ASSIGNMENTS", indexes = {
      @Index(name = "IDX_OA_PARKING_SHIFT", columnList = "PARKING_ID, SHIFT_ID"),
      @Index(name = "IDX_OA_OPERATOR", columnList = "OPERATOR_ID")
})
public class OperatorAssignmentEntity {

   @Id
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "operator_assignment_seq")
   @SequenceGenerator(name = "operator_assignment_seq", sequenceName = "SEQ_OPERATOR_ASSIGNMENT", allocationSize = 1)
   @Column(name = "ID")
   private Long id;

   @Column(name = "OPERATOR_ID", nullable = false)
   private Long operatorId;

   @Column(name = "OPERATOR_NAME", length = 200)
   private String operatorName;

   @Column(name = "PARKING_ID", nullable = false)
   private Long parkingId;

   @Column(name = "ZONE_ID")
   private Long zoneId;

   @Column(name = "SHIFT_ID", nullable = false)
   private Long shiftId;

   @Column(name = "START_DATE", nullable = false)
   private LocalDate startDate;

   @Column(name = "END_DATE")
   private LocalDate endDate;

   @Column(name = "CREATED_AT", nullable = false, updatable = false)
   private LocalDateTime createdAt;

   @Column(name = "CREATED_BY")
   private Long createdBy;

   @Column(name = "UPDATED_AT", nullable = false)
   private LocalDateTime updatedAt;

   @Column(name = "UPDATED_BY")
   private Long updatedBy;

   public Long getId() { return id; }
   public void setId(Long id) { this.id = id; }
   public Long getOperatorId() { return operatorId; }
   public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
   public String getOperatorName() { return operatorName; }
   public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
   public Long getParkingId() { return parkingId; }
   public void setParkingId(Long parkingId) { this.parkingId = parkingId; }
   public Long getZoneId() { return zoneId; }
   public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
   public Long getShiftId() { return shiftId; }
   public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
   public LocalDate getStartDate() { return startDate; }
   public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
   public LocalDate getEndDate() { return endDate; }
   public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
   public LocalDateTime getCreatedAt() { return createdAt; }
   public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
   public Long getCreatedBy() { return createdBy; }
   public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
   public LocalDateTime getUpdatedAt() { return updatedAt; }
   public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
   public Long getUpdatedBy() { return updatedBy; }
   public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
