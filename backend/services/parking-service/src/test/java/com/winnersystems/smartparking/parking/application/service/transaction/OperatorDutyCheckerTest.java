package com.winnersystems.smartparking.parking.application.service.transaction;

import com.winnersystems.smartparking.parking.application.port.output.OperatorAssignmentPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ShiftPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.OperatorNotOnDutyException;
import com.winnersystems.smartparking.parking.domain.model.OperatorAssignment;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.winnersystems.smartparking.parking.application.service.transaction.TransactionFixtures.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Regla 8: solo registra quien está de turno en ese parqueo o zona.
 */
class OperatorDutyCheckerTest {

   private OperatorAssignmentPersistencePort assignments;
   private FakeUser user;
   private OperatorDutyChecker checker;
   private final Parking parking = parking(1L);

   @BeforeEach
   void setUp() {
      assignments = mock(OperatorAssignmentPersistencePort.class);
      ShiftPersistencePort shifts = mock(ShiftPersistencePort.class);
      when(shifts.findById(1L)).thenReturn(Optional.of(shift(1L, "06:00", "11:59")));
      when(shifts.findById(3L)).thenReturn(Optional.of(shift(3L, "18:00", "05:59")));
      user = new FakeUser();
      checker = new OperatorDutyChecker(user, assignments, shifts);
   }

   private OperatorAssignment assignment(Long zoneId, Long shiftId, LocalDate start, LocalDate end) {
      return OperatorAssignment.create(OPERATOR, "Ana Ruiz", 1L, zoneId, shiftId, start, end, start, 9L);
   }

   @Test
   @DisplayName("de turno en todo el parqueo: puede registrar en cualquier zona")
   void parkingWideAssignment() {
      when(assignments.findAll(1L, null, OPERATOR)).thenReturn(List.of(assignment(null, 1L, TODAY, null)));
      assertThatCode(() -> checker.ensureOnDuty(OPERATOR, parking, 10L, NOW)).doesNotThrowAnyException();
   }

   @Test
   @DisplayName("asignado a otra zona o fuera del horario de su turno: 403")
   void otherZoneOrOutsideShift() {
      when(assignments.findAll(1L, null, OPERATOR)).thenReturn(List.of(assignment(20L, 1L, TODAY, null)));
      assertThatThrownBy(() -> checker.ensureOnDuty(OPERATOR, parking, 10L, NOW))
            .isInstanceOf(OperatorNotOnDutyException.class);

      when(assignments.findAll(1L, null, OPERATOR)).thenReturn(List.of(assignment(null, 1L, TODAY, null)));
      assertThatThrownBy(() -> checker.ensureOnDuty(OPERATOR, parking, 10L, NOW.withHour(13)))
            .isInstanceOf(OperatorNotOnDutyException.class);
   }

   @Test
   @DisplayName("turno nocturno: a las 02:00 cuenta la asignación del día anterior")
   void nightShiftBelongsToPreviousDay() {
      // Asignada solo la noche del 26: cubre el 27 a las 02:00, no el 26 a las 02:00
      when(assignments.findAll(1L, null, OPERATOR)).thenReturn(List.of(assignment(null, 3L, TODAY, TODAY)));
      LocalDateTime earlyNextDay = LocalDateTime.of(2026, 11, 27, 2, 0);
      assertThatCode(() -> checker.ensureOnDuty(OPERATOR, parking, 10L, earlyNextDay)).doesNotThrowAnyException();
      assertThatThrownBy(() -> checker.ensureOnDuty(OPERATOR, parking, 10L, TODAY.atTime(2, 0)))
            .isInstanceOf(OperatorNotOnDutyException.class);
   }

   @Test
   @DisplayName("con transactions.any-parking registra sin turno")
   void anyParkingPermission() {
      user.authorities.add(OperatorDutyChecker.ANY_PARKING_PERMISSION);
      assertThatCode(() -> checker.ensureOnDuty(OPERATOR, parking, 10L, NOW)).doesNotThrowAnyException();
      verifyNoInteractions(assignments);
   }
}
