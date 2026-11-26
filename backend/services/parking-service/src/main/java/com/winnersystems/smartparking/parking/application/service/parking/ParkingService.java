package com.winnersystems.smartparking.parking.application.service.parking;

import com.winnersystems.smartparking.parking.application.dto.command.CreateParkingCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateParkingCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingCapacity;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingDto;
import com.winnersystems.smartparking.parking.application.port.input.parking.*;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingCapacityPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.UserDirectoryPort;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Casos de uso de Parqueos.
 *
 * Reglas que aplica este servicio (las propias del parqueo están en la entidad):
 * - Código único sin distinguir mayúsculas, también frente a los eliminados (409).
 * - El responsable debe existir y estar activo en auth-service; su nombre se toma de allí.
 * - No se elimina un parqueo con vehículos dentro (409): primero deben salir.
 * - La capacidad se calcula desde los espacios reales en cada consulta.
 */
@Service
@Transactional
public class ParkingService implements
      CreateParkingUseCase,
      UpdateParkingUseCase,
      DeleteParkingUseCase,
      RestoreParkingUseCase,
      GetParkingUseCase,
      ListParkingsUseCase,
      ToggleParkingStatusUseCase {

   private final ParkingPersistencePort parkingPersistencePort;
   private final ParkingCapacityPort capacityPort;
   private final TransactionPersistencePort transactionPersistencePort;
   private final UserDirectoryPort userDirectoryPort;
   private final CurrentUserPort currentUserPort;

   public ParkingService(ParkingPersistencePort parkingPersistencePort,
                         ParkingCapacityPort capacityPort,
                         TransactionPersistencePort transactionPersistencePort,
                         UserDirectoryPort userDirectoryPort,
                         CurrentUserPort currentUserPort) {
      this.parkingPersistencePort = parkingPersistencePort;
      this.capacityPort = capacityPort;
      this.transactionPersistencePort = transactionPersistencePort;
      this.userDirectoryPort = userDirectoryPort;
      this.currentUserPort = currentUserPort;
   }

   // ========================= CREAR =========================

   @Override
   public ParkingDto createParking(CreateParkingCommand command) {
      Long userId = currentUserPort.requireCurrentUserId();
      Parking parking = Parking.create(command.name(), command.code(), command.description(),
            command.address(), command.latitude(), command.longitude(), userId);
      ensureCodeAvailable(parking.getCode());
      applyManager(parking, command.managerId());
      parking.changeDriverDocumentPolicy(Boolean.TRUE.equals(command.requireDriverDocument()));
      return toDto(parkingPersistencePort.save(parking));
   }

   // ========================= ACTUALIZAR =========================

   @Override
   public ParkingDto updateParking(Long parkingId, UpdateParkingCommand command) {
      Parking parking = findOrThrow(parkingId);
      parking.update(command.name(), command.description(), command.address(),
            command.latitude(), command.longitude());
      // Solo se consulta auth-service si el responsable cambió
      if (!Objects.equals(parking.getManagerId(), command.managerId())) {
         applyManager(parking, command.managerId());
      }
      if (command.status() != null && !command.status().isBlank()) {
         parking.changeStatus(command.status());
      }
      if (command.requireDriverDocument() != null) {
         parking.changeDriverDocumentPolicy(command.requireDriverDocument());
      }
      parking.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(parkingPersistencePort.save(parking));
   }

   // ========================= ELIMINAR Y RESTAURAR =========================

   /**
    * Eliminación lógica. Sus zonas, espacios, turnos y tarifas se conservan y
    * vuelven con él si se restaura.
    */
   @Override
   public void deleteParking(Long parkingId) {
      Parking parking = findOrThrow(parkingId);
      long inside = transactionPersistencePort.countActiveByParkingId(parkingId);
      if (inside > 0) {
         throw new ResourceConflictException("No se puede eliminar el parqueo " + parking.getName()
               + ": tiene " + inside + (inside == 1 ? " vehículo dentro" : " vehículos dentro")
               + ". Regístralos como salidos o desactiva el parqueo para que no reciba más.");
      }
      parking.markAsDeleted(currentUserPort.requireCurrentUserId());
      parkingPersistencePort.save(parking);
   }

   /** El código sigue reservado mientras está en la papelera, así que no hay conflicto al restaurar */
   @Override
   public ParkingDto restoreParking(Long parkingId) {
      Parking parking = parkingPersistencePort.findDeletedById(parkingId)
            .orElseThrow(() -> new ResourceNotFoundException("No hay un parqueo eliminado con ID: " + parkingId));
      parking.restore(currentUserPort.requireCurrentUserId());
      return toDto(parkingPersistencePort.save(parking));
   }

   // ========================= CONSULTAS =========================

   @Override
   @Transactional(readOnly = true)
   public ParkingDto getParkingById(Long parkingId) {
      return toDto(findOrThrow(parkingId));
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<ParkingDto> listAllParkings(int pageNumber, int pageSize, String search,
                                                    String status, boolean deleted) {
      String statusFilter = normalizeStatusFilter(status);
      PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, "name", "ASC");
      PageResult<Parking> page = parkingPersistencePort.findAll(pageRequest, search, statusFilter, deleted);
      Map<Long, ParkingCapacity> capacities =
            capacityPort.capacityOf(page.content().stream().map(Parking::getId).toList());
      return page.toPagedResponse(p -> toDto(p, capacities.getOrDefault(p.getId(), ParkingCapacity.empty())));
   }

   @Override
   @Transactional(readOnly = true)
   public List<ParkingDto> listAllActiveParkings() {
      List<Parking> parkings = parkingPersistencePort.findAllActive();
      Map<Long, ParkingCapacity> capacities = capacityPort.capacityOf(parkings.stream().map(Parking::getId).toList());
      return parkings.stream()
            .map(p -> toDto(p, capacities.getOrDefault(p.getId(), ParkingCapacity.empty())))
            .toList();
   }

   // ========================= ESTADO =========================
   // Cambiar a inactivo o mantenimiento no expulsa a nadie: los vehículos dentro
   // pueden salir; solo se bloquean las entradas nuevas.

   @Override
   public ParkingDto toggleParkingStatus(Long parkingId) {
      return changeStatus(parkingId, null);
   }

   @Override
   public ParkingDto activateParking(Long parkingId) {
      return changeStatus(parkingId, Parking.STATUS_ACTIVE);
   }

   @Override
   public ParkingDto deactivateParking(Long parkingId) {
      return changeStatus(parkingId, Parking.STATUS_INACTIVE);
   }

   @Override
   public ParkingDto setInMaintenance(Long parkingId) {
      return changeStatus(parkingId, Parking.STATUS_MAINTENANCE);
   }

   @Override
   public ParkingDto setOutOfService(Long parkingId) {
      return changeStatus(parkingId, Parking.STATUS_OUT_OF_SERVICE);
   }

   /** @param status null = alternar entre activo e inactivo */
   private ParkingDto changeStatus(Long parkingId, String status) {
      Parking parking = findOrThrow(parkingId);
      if (status == null) {
         parking.changeStatus(parking.isActive() ? Parking.STATUS_INACTIVE : Parking.STATUS_ACTIVE);
      } else {
         parking.changeStatus(status);
      }
      parking.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(parkingPersistencePort.save(parking));
   }

   // ========================= AUXILIARES =========================

   private Parking findOrThrow(Long parkingId) {
      return parkingPersistencePort.findById(parkingId)
            .orElseThrow(() -> new ResourceNotFoundException("Parqueo", parkingId));
   }

   private void ensureCodeAvailable(String code) {
      parkingPersistencePort.findByCodeIncludingDeleted(code).ifPresent(existing -> {
         if (existing.isDeleted()) {
            throw new ResourceConflictException("El código " + code + " pertenece al parqueo eliminado "
                  + existing.getName() + ". Restáuralo desde la papelera o usa otro código.");
         }
         throw new ResourceConflictException("Ya existe un parqueo con el código " + code);
      });
   }

   /** El responsable debe existir y estar activo; su nombre se toma de auth-service */
   private void applyManager(Parking parking, Long managerId) {
      if (managerId == null) {
         parking.removeManager();
         return;
      }
      UserDirectoryPort.UserSummary user = userDirectoryPort.findUser(managerId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario responsable", managerId));
      if (!user.active()) {
         throw new ResourceConflictException("El usuario " + user.fullName()
               + " está desactivado y no puede ser responsable de un parqueo");
      }
      parking.assignManager(user.id(), user.fullName());
   }

   private static String normalizeStatusFilter(String status) {
      if (status == null || status.isBlank()) {
         return null;
      }
      String value = status.trim().toUpperCase();
      if (!Parking.STATUSES.contains(value)) {
         throw new ParkingDomainException("Estado de parqueo inválido: " + status);
      }
      return value;
   }

   private ParkingDto toDto(Parking parking) {
      ParkingCapacity capacity = capacityPort.capacityOf(List.of(parking.getId()))
            .getOrDefault(parking.getId(), ParkingCapacity.empty());
      return toDto(parking, capacity);
   }

   private ParkingDto toDto(Parking p, ParkingCapacity c) {
      return new ParkingDto(
            p.getId(), p.getName(), p.getCode(), p.getDescription(), p.getAddress(),
            p.getLatitude(), p.getLongitude(), p.getManagerId(), p.getManagerName(),
            c.totalZones(), c.totalSpaces(), c.availableSpaces(), c.occupiedSpaces(),
            c.unavailableSpaces(), c.occupancyPercentage(),
            p.getStatus(), p.isOperational(), p.isDriverDocumentRequired(),
            p.getCreatedAt(), p.getCreatedBy(), p.getUpdatedAt(), p.getUpdatedBy(),
            p.getDeletedAt(), p.getDeletedBy()
      );
   }
}
