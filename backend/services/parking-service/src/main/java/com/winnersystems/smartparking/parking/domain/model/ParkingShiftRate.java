// domain/model/ParkingShiftRate.java
package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Horario y tarifa de un parqueo: en el turno X el parqueo cobra la tarifa Y.
 *
 * Vale para TODAS las zonas del parqueo (los turnos y tarifas son del parqueo, no de la zona).
 *
 * Reglas:
 * - Un turno aparece una sola vez por parqueo (UK en PARKING_ID + SHIFT_ID).
 * - Si a una hora no hay ninguna configuración activa, el parqueo no atiende a esa hora
 *   (regla de negocio 1): no recibe vehículos.
 * - Dos configuraciones activas del mismo parqueo no pueden tener turnos que se solapen,
 *   para que a cada minuto le corresponda una sola tarifa (lo valida el servicio, que
 *   conoce los horarios de los turnos).
 * - Es configuración, no historial: una transacción guarda su propia tarifa, así que
 *   cambiar o quitar una configuración no altera las estadías ya registradas.
 */
public class ParkingShiftRate {

   private Long id;
   private Long parkingId;
   private Long shiftId;
   private Long rateId;
   private boolean status;                 // true = el parqueo atiende en este turno

   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;

   // ========================= CONSTRUCTORES =========================

   public ParkingShiftRate() {
      this.status = true;
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
   }

   public static ParkingShiftRate create(Long parkingId, Long shiftId, Long rateId, boolean active, Long createdBy) {
      if (parkingId == null || shiftId == null || rateId == null) {
         throw new ParkingDomainException("Indica el parqueo, el turno y la tarifa");
      }
      ParkingShiftRate config = new ParkingShiftRate();
      config.parkingId = parkingId;
      config.shiftId = shiftId;
      config.rateId = rateId;
      config.status = active;
      config.createdBy = createdBy;
      config.updatedBy = createdBy;
      return config;
   }

   // ========================= NEGOCIO =========================

   /** Cambia la tarifa del turno. Las estadías ya registradas conservan la suya */
   public void changeRate(Long newRateId) {
      if (newRateId == null) {
         throw new ParkingDomainException("Indica la tarifa");
      }
      this.rateId = newRateId;
      touch();
   }

   public void activate() {
      this.status = true;
      touch();
   }

   public void deactivate() {
      this.status = false;
      touch();
   }

   public boolean isActive() {
      return status;
   }

   public boolean belongsTo(Long parkingId) {
      return this.parkingId != null && this.parkingId.equals(parkingId);
   }

   public void updateModifiedBy(Long userId) {
      this.updatedBy = userId;
      touch();
   }

   private void touch() {
      this.updatedAt = LocalDateTime.now();
   }

   // ========================= GETTERS Y SETTERS =========================
   // Los setters los usa el mapper de persistencia; la lógica usa los métodos de negocio

   public Long getId() { return id; }
   public void setId(Long id) { this.id = id; }

   public Long getParkingId() { return parkingId; }
   public void setParkingId(Long parkingId) { this.parkingId = parkingId; }

   public Long getShiftId() { return shiftId; }
   public void setShiftId(Long shiftId) { this.shiftId = shiftId; }

   public Long getRateId() { return rateId; }
   public void setRateId(Long rateId) { this.rateId = rateId; }

   public boolean getStatus() { return status; }
   public void setStatus(boolean status) { this.status = status; }

   public LocalDateTime getCreatedAt() { return createdAt; }
   public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

   public Long getCreatedBy() { return createdBy; }
   public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

   public LocalDateTime getUpdatedAt() { return updatedAt; }
   public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

   public Long getUpdatedBy() { return updatedBy; }
   public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

   // ========================= EQUALS, HASHCODE Y TOSTRING =========================

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ParkingShiftRate that = (ParkingShiftRate) o;
      return id != null && id.equals(that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(id);
   }

   @Override
   public String toString() {
      return "ParkingShiftRate{id=" + id + ", parkingId=" + parkingId + ", shiftId=" + shiftId
            + ", rateId=" + rateId + ", status=" + status + '}';
   }
}
