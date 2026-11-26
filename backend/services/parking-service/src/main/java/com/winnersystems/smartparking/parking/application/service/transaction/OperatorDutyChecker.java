package com.winnersystems.smartparking.parking.application.service.transaction;

import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.OperatorAssignmentPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ShiftPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.OperatorNotOnDutyException;
import com.winnersystems.smartparking.parking.domain.model.OperatorAssignment;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Shift;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Regla de negocio 8: solo registra entradas y salidas quien está de turno en ese parqueo.
 *
 * Está de turno si tiene una asignación:
 * - del parqueo completo, o de la zona de la estadía;
 * - vigente en la fecha del turno (un turno que cruza la medianoche, a las 02:00, pertenece
 *   al día anterior);
 * - y la hora actual cae dentro del horario de su turno, que debe seguir activo.
 *
 * Quien tiene el permiso transactions.any-parking (ADMIN) puede registrar en cualquier parqueo
 * sin turno: sirve para emergencias y para probar el sistema.
 */
@Component
public class OperatorDutyChecker {

   /** Permiso para registrar en cualquier parqueo sin estar de turno */
   public static final String ANY_PARKING_PERMISSION = "transactions.any-parking";

   private final CurrentUserPort currentUserPort;
   private final OperatorAssignmentPersistencePort assignmentPort;
   private final ShiftPersistencePort shiftPort;

   public OperatorDutyChecker(CurrentUserPort currentUserPort,
                              OperatorAssignmentPersistencePort assignmentPort,
                              ShiftPersistencePort shiftPort) {
      this.currentUserPort = currentUserPort;
      this.assignmentPort = assignmentPort;
      this.shiftPort = shiftPort;
   }

   /**
    * @param operatorId usuario que registra
    * @param parking    parqueo de la estadía
    * @param zoneId     zona de la estadía
    * @param now        hora del registro
    * @throws OperatorNotOnDutyException si no está de turno allí (403)
    */
   public void ensureOnDuty(Long operatorId, Parking parking, Long zoneId, LocalDateTime now) {
      if (currentUserPort.hasAuthority(ANY_PARKING_PERMISSION)) {
         return;
      }
      boolean onDuty = operatorId != null && assignmentPort.findAll(parking.getId(), null, operatorId).stream()
            .filter(a -> a.isParkingWide() || Objects.equals(a.getZoneId(), zoneId))
            .anyMatch(a -> coversNow(a, now));
      if (!onDuty) {
         throw new OperatorNotOnDutyException(parking.getName());
      }
   }

   private boolean coversNow(OperatorAssignment assignment, LocalDateTime now) {
      Shift shift = shiftPort.findById(assignment.getShiftId()).orElse(null);
      if (shift == null || !shift.isUsable() || !shift.isWithinShift(now.toLocalTime())) {
         return false;
      }
      // En un turno nocturno (18:00-05:59), las horas después de medianoche son del día anterior
      LocalDate shiftDay = shift.crossesMidnight() && now.toLocalTime().isBefore(shift.getStartTime())
            ? now.toLocalDate().minusDays(1)
            : now.toLocalDate();
      return assignment.isActiveOn(shiftDay);
   }
}
