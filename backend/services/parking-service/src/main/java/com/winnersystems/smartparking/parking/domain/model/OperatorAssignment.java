package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Asignación de un operador (usuario de auth-service) a un parqueo en un turno.
 *
 * - Sin zona: el operador atiende todo el parqueo en ese turno.
 * - Con zona: atiende solo esa zona (cada zona puede tener el suyo).
 * - Las fechas son inclusivas: del inicio al fin, ambos días incluidos; sin fin = indefinida.
 *
 * Reglas que dependen de otras asignaciones (no se cruzan horarios del mismo operador,
 * un operador de parqueo o uno por zona en cada turno) las valida el servicio.
 *
 * Es historial: no tiene papelera. Una asignación que ya empezó se finaliza (queda
 * registrada hasta el día anterior); una que aún no empezó se cancela (se borra).
 */
public class OperatorAssignment {

   public static final String STATUS_PENDING = "PENDING";
   public static final String STATUS_ACTIVE = "ACTIVE";
   public static final String STATUS_ENDED = "ENDED";

   /** Fin para comparar rangos de una asignación indefinida (Oracle admite hasta el año 9999) */
   public static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

   private Long id;
   private Long operatorId;
   private String operatorName;            // copia del nombre al asignar (auth-service es la fuente)
   private Long parkingId;
   private Long zoneId;                    // null = todo el parqueo
   private Long shiftId;
   private LocalDate startDate;
   private LocalDate endDate;              // null = indefinida

   private LocalDateTime createdAt;
   private Long createdBy;
   private LocalDateTime updatedAt;
   private Long updatedBy;

   public OperatorAssignment() {
      this.createdAt = LocalDateTime.now();
      this.updatedAt = this.createdAt;
   }

   /**
    * @param today fecha actual: una asignación nueva no puede empezar en el pasado
    */
   public static OperatorAssignment create(Long operatorId, String operatorName, Long parkingId, Long zoneId,
                                           Long shiftId, LocalDate startDate, LocalDate endDate,
                                           LocalDate today, Long createdBy) {
      if (operatorId == null) throw new ParkingDomainException("Indica el operador");
      if (parkingId == null) throw new ParkingDomainException("Indica el parqueo");
      if (shiftId == null) throw new ParkingDomainException("Indica el turno");
      LocalDate start = startDate != null ? startDate : today;
      if (start.isBefore(today)) {
         throw new ParkingDomainException("La asignación no puede empezar en una fecha pasada");
      }
      requireValidRange(start, endDate);

      OperatorAssignment a = new OperatorAssignment();
      a.operatorId = operatorId;
      a.operatorName = operatorName;
      a.parkingId = parkingId;
      a.zoneId = zoneId;
      a.shiftId = shiftId;
      a.startDate = start;
      a.endDate = endDate;
      a.createdBy = createdBy;
      a.updatedBy = createdBy;
      return a;
   }

   // ========================= ESTADO =========================

   public String statusOn(LocalDate today) {
      if (startDate.isAfter(today)) {
         return STATUS_PENDING;
      }
      if (endDate == null || !endDate.isBefore(today)) {
         return STATUS_ACTIVE;
      }
      return STATUS_ENDED;
   }

   public boolean isActiveOn(LocalDate day) {
      return STATUS_ACTIVE.equals(statusOn(day));
   }

   public boolean hasStarted(LocalDate today) {
      return !startDate.isAfter(today);
   }

   public boolean hasEnded(LocalDate today) {
      return STATUS_ENDED.equals(statusOn(today));
   }

   public boolean isParkingWide() {
      return zoneId == null;
   }

   /** Las dos asignaciones comparten al menos un día */
   public boolean overlapsDates(LocalDate otherStart, LocalDate otherEnd) {
      LocalDate end = endDate != null ? endDate : OPEN_END;
      LocalDate oEnd = otherEnd != null ? otherEnd : OPEN_END;
      return !startDate.isAfter(oEnd) && !otherStart.isAfter(end);
   }

   // ========================= CAMBIOS =========================

   /**
    * Cambia las fechas. Si ya empezó, el inicio no cambia (es historial) y el fin no puede
    * quedar en el pasado: para terminarla se usa finish().
    */
   public void changeDates(LocalDate newStart, LocalDate newEnd, LocalDate today, Long userId) {
      if (hasEnded(today)) {
         throw new ParkingDomainException("La asignación ya terminó: crea una nueva");
      }
      LocalDate start = newStart != null ? newStart : startDate;
      if (hasStarted(today)) {
         if (!start.equals(startDate)) {
            throw new ParkingDomainException("La asignación ya empezó: su fecha de inicio no cambia");
         }
      } else if (start.isBefore(today)) {
         throw new ParkingDomainException("La asignación no puede empezar en una fecha pasada");
      }
      if (newEnd != null && newEnd.isBefore(today)) {
         throw new ParkingDomainException("La fecha de fin no puede ser pasada: para terminarla hoy usa Finalizar");
      }
      requireValidRange(start, newEnd);
      this.startDate = start;
      this.endDate = newEnd;
      touch(userId);
   }

   /**
    * Termina la asignación ahora: el operador deja de estar asignado desde hoy.
    *
    * @return true si queda como historial (terminó ayer); false si no llegó a cumplir un
    *         día completo (empieza hoy o más adelante) y debe cancelarse (borrarse)
    */
   public boolean finish(LocalDate today, Long userId) {
      if (hasEnded(today)) {
         throw new ParkingDomainException("La asignación ya está finalizada");
      }
      if (!startDate.isBefore(today)) {
         return false;
      }
      this.endDate = today.minusDays(1);
      touch(userId);
      return true;
   }

   private static void requireValidRange(LocalDate start, LocalDate end) {
      if (end != null && end.isBefore(start)) {
         throw new ParkingDomainException("La fecha de fin no puede ser anterior a la de inicio");
      }
   }

   private void touch(Long userId) {
      this.updatedBy = userId;
      this.updatedAt = LocalDateTime.now();
   }

   // ========================= GETTERS Y SETTERS =========================
   // Los setters los usa el mapper de persistencia; la lógica usa los métodos de negocio

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

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      OperatorAssignment that = (OperatorAssignment) o;
      return id != null && id.equals(that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hashCode(id);
   }

   @Override
   public String toString() {
      return "OperatorAssignment{id=" + id + ", operatorId=" + operatorId + ", parkingId=" + parkingId
            + ", zoneId=" + zoneId + ", shiftId=" + shiftId + ", " + startDate + " - " + endDate + '}';
   }
}
