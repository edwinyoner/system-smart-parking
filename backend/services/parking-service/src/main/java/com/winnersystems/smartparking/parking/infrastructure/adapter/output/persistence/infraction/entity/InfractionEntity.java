package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.infraction.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tabla INFRACTIONS (Oracle). Registro histórico: sin borrado lógico, se anula.
 * La auditoría la llena el dominio (los avisos de pago llegan con token de servicio, sin usuario).
 */
@Entity
@Table(name = "INFRACTIONS", indexes = {
      @Index(name = "IDX_INF_PARKING_DETECTED", columnList = "PARKING_ID, DETECTED_AT"),
      @Index(name = "IDX_INF_STATUS", columnList = "STATUS"),
      @Index(name = "IDX_INF_TRANSACTION", columnList = "TRANSACTION_ID"),
      @Index(name = "IDX_INF_VEHICLE", columnList = "VEHICLE_ID")
})
public class InfractionEntity {

   @Id
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "infraction_seq")
   @SequenceGenerator(name = "infraction_seq", sequenceName = "INFRACTION_SEQ", allocationSize = 1)
   @Column(name = "INFRACTION_ID")
   private Long id;

   @Column(name = "INFRACTION_CODE", unique = true, length = 20)
   private String infractionCode;

   @Column(name = "PARKING_ID", nullable = false)
   private Long parkingId;

   @Column(name = "ZONE_ID")
   private Long zoneId;

   @Column(name = "SPACE_ID")
   private Long spaceId;

   @Column(name = "TRANSACTION_ID")
   private Long transactionId;

   @Column(name = "VEHICLE_ID", nullable = false)
   private Long vehicleId;

   @Column(name = "PLATE_NUMBER", nullable = false, length = 10)
   private String plateNumber;

   @Column(name = "CUSTOMER_ID")
   private Long customerId;

   @Column(name = "INFRACTION_TYPE", nullable = false, length = 30)
   private String infractionType;

   @Column(name = "SEVERITY", nullable = false, length = 20)
   private String severity;

   @Column(name = "DETECTED_AT", nullable = false)
   private LocalDateTime detectedAt;

   @Column(name = "DETECTED_BY")
   private Long detectedBy;

   @Column(name = "DETECTED_BY_NAME", length = 200)
   private String detectedByName;

   @Column(name = "DETECTION_METHOD", length = 20)
   private String detectionMethod;

   @Column(name = "DESCRIPTION", nullable = false, length = 1000)
   private String description;

   @Column(name = "EVIDENCE", length = 2600)
   private String evidence;

   @Column(name = "FINE_AMOUNT", precision = 10, scale = 2)
   private BigDecimal fineAmount;

   @Column(name = "CURRENCY", length = 3)
   private String currency;

   @Column(name = "FINE_DUE_DATE")
   private LocalDate fineDueDate;

   @Column(name = "STATUS", nullable = false, length = 20)
   private String status;

   @Column(name = "RESOLUTION_TYPE", length = 20)
   private String resolutionType;

   @Column(name = "RESOLUTION", length = 500)
   private String resolution;

   @Column(name = "RESOLVED_AT")
   private LocalDateTime resolvedAt;

   @Column(name = "RESOLVED_BY")
   private Long resolvedBy;

   @Column(name = "FINE_PAID", nullable = false)
   private Boolean finePaid;

   @Column(name = "FINE_PAID_AT")
   private LocalDateTime finePaidAt;

   @Column(name = "FINE_PAID_AMOUNT", precision = 10, scale = 2)
   private BigDecimal finePaidAmount;

   @Column(name = "FINE_PAYMENT_REFERENCE", length = 50)
   private String finePaymentReference;

   @Column(name = "NOTES", length = 500)
   private String notes;

   @Column(name = "CREATED_AT", nullable = false, updatable = false)
   private LocalDateTime createdAt;

   @Column(name = "CREATED_BY", updatable = false)
   private Long createdBy;

   @Column(name = "UPDATED_AT", nullable = false)
   private LocalDateTime updatedAt;

   @Column(name = "UPDATED_BY")
   private Long updatedBy;

   public Long getId() { return id; }
   public void setId(Long id) { this.id = id; }
   public String getInfractionCode() { return infractionCode; }
   public void setInfractionCode(String infractionCode) { this.infractionCode = infractionCode; }
   public Long getParkingId() { return parkingId; }
   public void setParkingId(Long parkingId) { this.parkingId = parkingId; }
   public Long getZoneId() { return zoneId; }
   public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
   public Long getSpaceId() { return spaceId; }
   public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
   public Long getTransactionId() { return transactionId; }
   public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
   public Long getVehicleId() { return vehicleId; }
   public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
   public String getPlateNumber() { return plateNumber; }
   public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
   public Long getCustomerId() { return customerId; }
   public void setCustomerId(Long customerId) { this.customerId = customerId; }
   public String getInfractionType() { return infractionType; }
   public void setInfractionType(String infractionType) { this.infractionType = infractionType; }
   public String getSeverity() { return severity; }
   public void setSeverity(String severity) { this.severity = severity; }
   public LocalDateTime getDetectedAt() { return detectedAt; }
   public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
   public Long getDetectedBy() { return detectedBy; }
   public void setDetectedBy(Long detectedBy) { this.detectedBy = detectedBy; }
   public String getDetectedByName() { return detectedByName; }
   public void setDetectedByName(String detectedByName) { this.detectedByName = detectedByName; }
   public String getDetectionMethod() { return detectionMethod; }
   public void setDetectionMethod(String detectionMethod) { this.detectionMethod = detectionMethod; }
   public String getDescription() { return description; }
   public void setDescription(String description) { this.description = description; }
   public String getEvidence() { return evidence; }
   public void setEvidence(String evidence) { this.evidence = evidence; }
   public BigDecimal getFineAmount() { return fineAmount; }
   public void setFineAmount(BigDecimal fineAmount) { this.fineAmount = fineAmount; }
   public String getCurrency() { return currency; }
   public void setCurrency(String currency) { this.currency = currency; }
   public LocalDate getFineDueDate() { return fineDueDate; }
   public void setFineDueDate(LocalDate fineDueDate) { this.fineDueDate = fineDueDate; }
   public String getStatus() { return status; }
   public void setStatus(String status) { this.status = status; }
   public String getResolutionType() { return resolutionType; }
   public void setResolutionType(String resolutionType) { this.resolutionType = resolutionType; }
   public String getResolution() { return resolution; }
   public void setResolution(String resolution) { this.resolution = resolution; }
   public LocalDateTime getResolvedAt() { return resolvedAt; }
   public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
   public Long getResolvedBy() { return resolvedBy; }
   public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
   public Boolean getFinePaid() { return finePaid; }
   public void setFinePaid(Boolean finePaid) { this.finePaid = finePaid; }
   public LocalDateTime getFinePaidAt() { return finePaidAt; }
   public void setFinePaidAt(LocalDateTime finePaidAt) { this.finePaidAt = finePaidAt; }
   public BigDecimal getFinePaidAmount() { return finePaidAmount; }
   public void setFinePaidAmount(BigDecimal finePaidAmount) { this.finePaidAmount = finePaidAmount; }
   public String getFinePaymentReference() { return finePaymentReference; }
   public void setFinePaymentReference(String finePaymentReference) { this.finePaymentReference = finePaymentReference; }
   public String getNotes() { return notes; }
   public void setNotes(String notes) { this.notes = notes; }
   public LocalDateTime getCreatedAt() { return createdAt; }
   public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
   public Long getCreatedBy() { return createdBy; }
   public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
   public LocalDateTime getUpdatedAt() { return updatedAt; }
   public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
   public Long getUpdatedBy() { return updatedBy; }
   public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
