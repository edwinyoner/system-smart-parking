package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.domain.model.OperatorAssignment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OperatorAssignmentPersistencePort {

   OperatorAssignment save(OperatorAssignment assignment);

   Optional<OperatorAssignment> findById(Long id);

   void deleteById(Long id);

   /** Del parqueo, opcionalmente de una zona, de un operador, o ambos (los nulos no filtran) */
   List<OperatorAssignment> findAll(Long parkingId, Long zoneId, Long operatorId);

   /** Del operador, cuyas fechas se cruzan con el rango (end null = indefinido) */
   List<OperatorAssignment> findByOperatorOverlapping(Long operatorId, LocalDate start, LocalDate end);

   /** Del parqueo y turno, cuyas fechas se cruzan con el rango (end null = indefinido) */
   List<OperatorAssignment> findByParkingShiftOverlapping(Long parkingId, Long shiftId, LocalDate start, LocalDate end);

   /** Vigentes o futuras del parqueo en el turno (para no quitar un turno con operadores) */
   long countCurrentOrFutureByParkingAndShift(Long parkingId, Long shiftId, LocalDate today);
}
