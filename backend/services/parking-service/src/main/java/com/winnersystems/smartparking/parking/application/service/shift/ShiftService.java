package com.winnersystems.smartparking.parking.application.service.shift;

import com.winnersystems.smartparking.parking.application.dto.command.CreateShiftCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateShiftCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.ShiftDto;
import com.winnersystems.smartparking.parking.application.port.input.shift.*;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingShiftRatePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ShiftPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Shift;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso de los turnos horarios.
 *
 * Reglas:
 * - El código es único entre todos los turnos, también los eliminados (409 si se repite).
 * - Un turno que usa algún parqueo no se elimina (409 con los nombres de los parqueos).
 *   Sí se puede desactivar: esos parqueos dejan de operar en ese horario.
 * - La auditoría (createdBy, updatedBy, deletedBy) es el usuario del JWT.
 */
@Slf4j
@Service
@Transactional
public class ShiftService implements
      CreateShiftUseCase,
      UpdateShiftUseCase,
      DeleteShiftUseCase,
      GetShiftUseCase,
      ListShiftsUseCase,
      ToggleShiftStatusUseCase {

   private static final String RESOURCE = "Turno";

   private final ShiftPersistencePort shiftPersistencePort;
   private final ParkingShiftRatePersistencePort parkingShiftRatePersistencePort;
   private final CurrentUserPort currentUserPort;

   public ShiftService(
         ShiftPersistencePort shiftPersistencePort,
         ParkingShiftRatePersistencePort parkingShiftRatePersistencePort,
         CurrentUserPort currentUserPort) {
      this.shiftPersistencePort = shiftPersistencePort;
      this.parkingShiftRatePersistencePort = parkingShiftRatePersistencePort;
      this.currentUserPort = currentUserPort;
   }

   // ========================= CREATE =========================

   @Override
   public ShiftDto createShift(CreateShiftCommand command) {
      Long userId = currentUserPort.requireCurrentUserId();

      // El dominio valida nombre, código y horario, y normaliza el código a mayúsculas
      Shift shift = Shift.create(
            command.name(),
            command.code(),
            command.startTime(),
            command.endTime(),
            command.description(),
            command.status() == null || command.status(),
            userId);

      ensureCodeIsAvailable(shift.getCode());

      Shift saved = shiftPersistencePort.save(shift);
      log.info("Turno creado: id={}, código={}, usuario={}", saved.getId(), saved.getCode(), userId);
      return mapToDto(saved);
   }

   // ========================= UPDATE =========================

   @Override
   public ShiftDto updateShift(Long shiftId, UpdateShiftCommand command) {
      Shift shift = findShift(shiftId);

      shift.updateBasicInfo(command.name(), command.description());
      shift.updateSchedule(command.startTime(), command.endTime());

      if (command.status() != null && command.status() != shift.isActive()) {
         changeStatus(shift, command.status());
      }

      shift.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return mapToDto(shiftPersistencePort.save(shift));
   }

   // ========================= DELETE =========================

   @Override
   public void deleteShift(Long id) {
      Shift shift = findShift(id);

      // Un turno en uso no se elimina: el parqueo perdería ese horario sin aviso
      List<String> parkings = parkingShiftRatePersistencePort.findParkingNamesUsingShift(id);
      if (!parkings.isEmpty()) {
         throw new ResourceConflictException(
               "No se puede eliminar el turno " + shift.getName() + ": lo usa " + describe(parkings)
                     + ". Quítalo de esos parqueos o desactívalo.");
      }

      shift.markAsDeleted(currentUserPort.requireCurrentUserId());
      shiftPersistencePort.save(shift);
      log.info("Turno eliminado (soft delete): id={}", id);
   }

   // ========================= GET =========================

   @Override
   @Transactional(readOnly = true)
   public ShiftDto getShiftById(Long id) {
      return mapToDto(findShift(id));
   }

   // ========================= LIST =========================

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<ShiftDto> listAllShifts(int page, int size, String search, Boolean status) {
      PageRequest pageRequest = PageRequest.of(page, size, "id", "ASC");
      PageResult<Shift> result = shiftPersistencePort.findAll(pageRequest, search, status);
      return result.toPagedResponse(this::mapToDto);
   }

   @Override
   @Transactional(readOnly = true)
   public List<ShiftDto> listAllActiveShifts() {
      return shiftPersistencePort.findAllActive().stream()
            .map(this::mapToDto)
            .toList();
   }

   // ========================= ESTADO =========================

   @Override
   public ShiftDto toggleShiftStatus(Long id) {
      Shift shift = findShift(id);
      return saveStatus(shift, !shift.isActive());
   }

   @Override
   public ShiftDto activateShift(Long id) {
      return saveStatus(findShift(id), true);
   }

   @Override
   public ShiftDto deactivateShift(Long id) {
      return saveStatus(findShift(id), false);
   }

   private ShiftDto saveStatus(Shift shift, boolean active) {
      changeStatus(shift, active);
      shift.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return mapToDto(shiftPersistencePort.save(shift));
   }

   /** Desactivar un turno en uso está permitido; queda registrado en el log */
   private void changeStatus(Shift shift, boolean active) {
      if (active) {
         shift.activate();
         return;
      }
      List<String> parkings = parkingShiftRatePersistencePort.findParkingNamesUsingShift(shift.getId());
      if (!parkings.isEmpty()) {
         log.warn("Turno {} desactivado estando en uso por: {}", shift.getCode(), parkings);
      }
      shift.deactivate();
   }

   // ========================= AUXILIARES =========================

   private Shift findShift(Long id) {
      return shiftPersistencePort.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
   }

   private void ensureCodeIsAvailable(String code) {
      shiftPersistencePort.findByCodeIncludingDeleted(code).ifPresent(existing -> {
         throw new ResourceConflictException(existing.isDeleted()
               ? "El código " + code + " pertenece a un turno eliminado. Usa otro código."
               : "Ya existe un turno con el código " + code);
      });
   }

   /** "el parqueo San Martín" o "los parqueos San Martín y Las Banderas" */
   private static String describe(List<String> parkings) {
      if (parkings.size() == 1) {
         return "el parqueo " + parkings.get(0);
      }
      String last = parkings.get(parkings.size() - 1);
      return "los parqueos " + String.join(", ", parkings.subList(0, parkings.size() - 1)) + " y " + last;
   }

   private ShiftDto mapToDto(Shift shift) {
      List<String> usedBy = shift.getId() == null
            ? List.of()
            : parkingShiftRatePersistencePort.findParkingNamesUsingShift(shift.getId());
      return new ShiftDto(
            shift.getId(),
            shift.getName(),
            shift.getCode(),
            shift.getDescription(),
            shift.getStartTime(),
            shift.getEndTime(),
            shift.getStatus(),
            shift.crossesMidnight(),
            shift.getDurationHours(),
            usedBy,
            shift.getCreatedAt(),
            shift.getCreatedBy(),
            shift.getUpdatedAt(),
            shift.getUpdatedBy()
      );
   }
}
