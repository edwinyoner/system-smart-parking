package com.winnersystems.smartparking.parking.application.service.operatorassignment;

import com.winnersystems.smartparking.parking.application.dto.command.AssignOperatorCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateOperatorAssignmentCommand;
import com.winnersystems.smartparking.parking.application.dto.query.OperatorAssignmentDto;
import com.winnersystems.smartparking.parking.application.port.input.operatorassignment.ManageOperatorAssignmentsUseCase;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.OperatorAssignment;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Shift;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asignación de operadores a parqueos y zonas por turno.
 *
 * Reglas:
 * - El parqueo debe estar vigente; la zona (si se indica) vigente y de ese parqueo.
 * - El turno debe estar activo en el horario del parqueo (ParkingShiftRate activa): no se
 *   asigna personal a un horario en que el parqueo no atiende (409).
 * - El operador debe existir y estar activo en auth-service; su nombre se copia.
 * - En un turno y fechas dados, el parqueo tiene un operador para todo el parqueo
 *   O uno por zona, no ambos (409).
 * - Un mismo operador no puede tener dos asignaciones que se crucen en fechas y en
 *   horario de turno, aunque sean de parqueos distintos (409): no puede estar en dos
 *   lugares a la vez.
 */
@Service
@Transactional
public class OperatorAssignmentService implements ManageOperatorAssignmentsUseCase {

   private final OperatorAssignmentPersistencePort assignmentPort;
   private final ParkingPersistencePort parkingPort;
   private final ZonePersistencePort zonePort;
   private final ShiftPersistencePort shiftPort;
   private final ParkingShiftRatePersistencePort shiftRatePort;
   private final UserDirectoryPort userDirectoryPort;
   private final CurrentUserPort currentUserPort;
   private final Clock clock;

   public OperatorAssignmentService(OperatorAssignmentPersistencePort assignmentPort,
                                    ParkingPersistencePort parkingPort,
                                    ZonePersistencePort zonePort,
                                    ShiftPersistencePort shiftPort,
                                    ParkingShiftRatePersistencePort shiftRatePort,
                                    UserDirectoryPort userDirectoryPort,
                                    CurrentUserPort currentUserPort,
                                    Clock clock) {
      this.assignmentPort = assignmentPort;
      this.parkingPort = parkingPort;
      this.zonePort = zonePort;
      this.shiftPort = shiftPort;
      this.shiftRatePort = shiftRatePort;
      this.userDirectoryPort = userDirectoryPort;
      this.currentUserPort = currentUserPort;
      this.clock = clock;
   }

   // ========================= ASIGNAR =========================

   @Override
   public OperatorAssignmentDto assign(AssignOperatorCommand command) {
      LocalDate today = LocalDate.now(clock);
      Parking parking = findParking(command.parkingId());
      if (command.zoneId() != null) {
         Zone zone = zonePort.findById(command.zoneId())
               .orElseThrow(() -> new ResourceNotFoundException("Zona", command.zoneId()));
         if (!zone.belongsTo(parking.getId())) {
            throw new ParkingDomainException("La zona " + zone.getName() + " no pertenece al parqueo " + parking.getName());
         }
      }
      Shift shift = shiftPort.findById(command.shiftId())
            .orElseThrow(() -> new ResourceNotFoundException("Turno", command.shiftId()));
      ensureShiftOperates(parking, shift);

      UserDirectoryPort.UserSummary user = userDirectoryPort.findUser(command.operatorId())
            .orElseThrow(() -> new ResourceNotFoundException("Usuario", command.operatorId()));
      if (!user.active()) {
         throw new ResourceConflictException("El usuario " + user.fullName() + " está desactivado y no puede ser asignado");
      }

      OperatorAssignment assignment = OperatorAssignment.create(user.id(), user.fullName(), parking.getId(),
            command.zoneId(), shift.getId(), command.startDate(), command.endDate(), today,
            currentUserPort.requireCurrentUserId());
      ensureNoConflicts(assignment, shift);
      return toDto(assignmentPort.save(assignment));
   }

   // ========================= CAMBIAR FECHAS =========================

   @Override
   public OperatorAssignmentDto update(Long assignmentId, UpdateOperatorAssignmentCommand command) {
      OperatorAssignment assignment = findAssignment(assignmentId);
      assignment.changeDates(command.startDate(), command.endDate(), LocalDate.now(clock),
            currentUserPort.requireCurrentUserId());
      Shift shift = shiftPort.findByIdIncludingDeleted(assignment.getShiftId())
            .orElseThrow(() -> new ResourceNotFoundException("Turno", assignment.getShiftId()));
      ensureNoConflicts(assignment, shift);
      return toDto(assignmentPort.save(assignment));
   }

   // ========================= FINALIZAR =========================

   @Override
   public Optional<OperatorAssignmentDto> finish(Long assignmentId) {
      OperatorAssignment assignment = findAssignment(assignmentId);
      boolean keep = assignment.finish(LocalDate.now(clock), currentUserPort.requireCurrentUserId());
      if (!keep) {
         assignmentPort.deleteById(assignmentId);
         return Optional.empty();
      }
      return Optional.of(toDto(assignmentPort.save(assignment)));
   }

   // ========================= CONSULTAS =========================

   @Override
   @Transactional(readOnly = true)
   public OperatorAssignmentDto get(Long assignmentId) {
      return toDto(findAssignment(assignmentId));
   }

   @Override
   @Transactional(readOnly = true)
   public List<OperatorAssignmentDto> list(Long parkingId, Long zoneId, Long operatorId, String status) {
      if (parkingId == null && operatorId == null) {
         throw new ParkingDomainException("Indica el parqueo o el operador");
      }
      if (parkingId != null) {
         findParking(parkingId);
      }
      return filterAndMap(assignmentPort.findAll(parkingId, zoneId, operatorId), normalizeStatus(status));
   }

   @Override
   @Transactional(readOnly = true)
   public List<OperatorAssignmentDto> listMine(String status) {
      return filterAndMap(assignmentPort.findAll(null, null, currentUserPort.requireCurrentUserId()),
            normalizeStatus(status));
   }

   // ========================= REGLAS =========================

   private void ensureShiftOperates(Parking parking, Shift shift) {
      boolean operates = shift.isUsable() && shiftRatePort.findByParkingId(parking.getId()).stream()
            .anyMatch(c -> c.isActive() && c.getShiftId().equals(shift.getId()));
      if (!operates) {
         throw new ResourceConflictException("El parqueo " + parking.getName() + " no atiende en el turno "
               + shift.getName() + ": actívalo primero en sus horarios y tarifas");
      }
   }

   private void ensureNoConflicts(OperatorAssignment candidate, Shift shift) {
      LocalDate start = candidate.getStartDate();
      LocalDate end = candidate.getEndDate();

      // 1. Un operador de parqueo o uno por zona en el mismo turno
      for (OperatorAssignment other : assignmentPort.findByParkingShiftOverlapping(
            candidate.getParkingId(), candidate.getShiftId(), start, end)) {
         if (other.equals(candidate)) {
            continue;
         }
         if (candidate.isParkingWide() || other.isParkingWide()) {
            throw new ResourceConflictException(other.isParkingWide()
                  ? other.getOperatorName() + " ya atiende todo el parqueo en este turno y fechas"
                  : "El parqueo ya tiene operadores por zona en este turno y fechas (por ejemplo "
                    + other.getOperatorName() + "): asigna por zona o finaliza esas asignaciones");
         }
         if (other.getZoneId().equals(candidate.getZoneId())) {
            throw new ResourceConflictException(other.getOperatorName()
                  + " ya atiende esta zona en este turno y fechas");
         }
      }

      // 2. El operador no puede estar en dos sitios a la vez
      List<OperatorAssignment> mine = assignmentPort.findByOperatorOverlapping(candidate.getOperatorId(), start, end);
      Map<Long, Shift> shifts = loadShifts(mine.stream().map(OperatorAssignment::getShiftId).collect(Collectors.toSet()));
      for (OperatorAssignment other : mine) {
         if (other.equals(candidate)) {
            continue;
         }
         Shift otherShift = shifts.get(other.getShiftId());
         if (other.getShiftId().equals(shift.getId()) || (otherShift != null && otherShift.overlapsWith(shift))) {
            String where = parkingPort.findByIdIncludingDeleted(other.getParkingId()).map(Parking::getName).orElse("otro parqueo");
            throw new ResourceConflictException(candidate.getOperatorName() + " ya tiene una asignación en "
                  + where + " con un horario que se cruza ("
                  + (otherShift != null ? otherShift.getName() + " " + otherShift.getTimeRange() : "turno " + other.getShiftId())
                  + ") en esas fechas");
         }
      }
   }

   // ========================= AUXILIARES =========================

   private Parking findParking(Long parkingId) {
      return parkingPort.findById(parkingId).orElseThrow(() -> new ResourceNotFoundException("Parqueo", parkingId));
   }

   private OperatorAssignment findAssignment(Long id) {
      return assignmentPort.findById(id).orElseThrow(() -> new ResourceNotFoundException("Asignación", id));
   }

   private static String normalizeStatus(String status) {
      if (status == null || status.isBlank()) {
         return null;
      }
      String value = status.trim().toUpperCase();
      if (!Set.of(OperatorAssignment.STATUS_PENDING, OperatorAssignment.STATUS_ACTIVE,
            OperatorAssignment.STATUS_ENDED).contains(value)) {
         throw new ParkingDomainException("Estado de asignación inválido: " + status);
      }
      return value;
   }

   private Map<Long, Shift> loadShifts(Set<Long> ids) {
      Map<Long, Shift> map = new HashMap<>();
      for (Long id : ids) {
         shiftPort.findByIdIncludingDeleted(id).ifPresent(s -> map.put(id, s));
      }
      return map;
   }

   private List<OperatorAssignmentDto> filterAndMap(List<OperatorAssignment> assignments, String status) {
      LocalDate today = LocalDate.now(clock);
      List<OperatorAssignment> filtered = assignments.stream()
            .filter(a -> status == null || status.equals(a.statusOn(today)))
            .toList();
      // Parqueos, zonas y turnos se cargan una vez por ID
      Map<Long, Parking> parkings = parkingPort.findAllByIdsIncludingDeleted(
                  filtered.stream().map(OperatorAssignment::getParkingId).distinct().toList()).stream()
            .collect(Collectors.toMap(Parking::getId, Function.identity()));
      Map<Long, Zone> zones = zonePort.findAllByIdsIncludingDeleted(
                  filtered.stream().map(OperatorAssignment::getZoneId).filter(Objects::nonNull).distinct().toList()).stream()
            .collect(Collectors.toMap(Zone::getId, Function.identity()));
      Map<Long, Shift> shifts = loadShifts(filtered.stream().map(OperatorAssignment::getShiftId).collect(Collectors.toSet()));
      return filtered.stream()
            .map(a -> toDto(a, parkings.get(a.getParkingId()),
                  a.getZoneId() != null ? zones.get(a.getZoneId()) : null, shifts.get(a.getShiftId())))
            .sorted(Comparator.comparing(OperatorAssignmentDto::startDate).reversed())
            .toList();
   }

   private OperatorAssignmentDto toDto(OperatorAssignment a) {
      Parking parking = parkingPort.findByIdIncludingDeleted(a.getParkingId()).orElse(null);
      Zone zone = a.getZoneId() != null ? zonePort.findByIdIncludingDeleted(a.getZoneId()).orElse(null) : null;
      Shift shift = shiftPort.findByIdIncludingDeleted(a.getShiftId()).orElse(null);
      return toDto(a, parking, zone, shift);
   }

   private OperatorAssignmentDto toDto(OperatorAssignment a, Parking parking, Zone zone, Shift shift) {
      LocalDateTime now = LocalDateTime.now(clock);
      String status = a.statusOn(now.toLocalDate());
      boolean onDuty = shift != null && a.isActiveOn(now.toLocalDate()) && shift.isWithinShift(now.toLocalTime());
      return new OperatorAssignmentDto(
            a.getId(), a.getOperatorId(), a.getOperatorName(),
            a.getParkingId(), parking != null ? parking.getName() : null, parking != null ? parking.getCode() : null,
            a.isParkingWide() ? "PARKING" : "ZONE",
            a.getZoneId(), zone != null ? zone.getName() : null, zone != null ? zone.getCode() : null,
            a.getShiftId(), shift != null ? shift.getName() : null,
            shift != null ? shift.getStartTime() : null, shift != null ? shift.getEndTime() : null,
            shift != null && shift.crossesMidnight(),
            a.getStartDate(), a.getEndDate(), status, onDuty,
            a.getCreatedAt(), a.getCreatedBy(), a.getUpdatedAt(), a.getUpdatedBy()
      );
   }
}
