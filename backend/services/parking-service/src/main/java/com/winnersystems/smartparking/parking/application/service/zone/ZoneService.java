package com.winnersystems.smartparking.parking.application.service.zone;

import com.winnersystems.smartparking.parking.application.dto.command.CreateZoneCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateZoneCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneCapacity;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneDto;
import com.winnersystems.smartparking.parking.application.port.input.zone.*;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingCapacityPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ZonePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Casos de uso de Zonas.
 *
 * Reglas que aplica este servicio (las propias de la zona están en la entidad):
 * - Se crea dentro de un parqueo vigente (404 si no existe o está eliminado).
 * - Código único dentro del parqueo, también frente a las eliminadas; nombre único
 *   dentro del parqueo entre las vigentes (409).
 * - No se elimina una zona con vehículos dentro (409).
 * - Solo se restaura si su parqueo no está eliminado (409).
 * - La capacidad se calcula desde los espacios reales.
 */
@Service
@Transactional
public class ZoneService implements
      CreateZoneUseCase,
      UpdateZoneUseCase,
      DeleteZoneUseCase,
      RestoreZoneUseCase,
      GetZoneUseCase,
      ListZonesUseCase,
      ToggleZoneStatusUseCase {

   private final ZonePersistencePort zonePersistencePort;
   private final ParkingPersistencePort parkingPersistencePort;
   private final ParkingCapacityPort capacityPort;
   private final TransactionPersistencePort transactionPersistencePort;
   private final CurrentUserPort currentUserPort;

   public ZoneService(ZonePersistencePort zonePersistencePort,
                      ParkingPersistencePort parkingPersistencePort,
                      ParkingCapacityPort capacityPort,
                      TransactionPersistencePort transactionPersistencePort,
                      CurrentUserPort currentUserPort) {
      this.zonePersistencePort = zonePersistencePort;
      this.parkingPersistencePort = parkingPersistencePort;
      this.capacityPort = capacityPort;
      this.transactionPersistencePort = transactionPersistencePort;
      this.currentUserPort = currentUserPort;
   }

   // ========================= CREAR =========================

   @Override
   public ZoneDto createZone(CreateZoneCommand command) {
      if (command.parkingId() == null) {
         throw new ParkingDomainException("La zona debe pertenecer a un parqueo");
      }
      Parking parking = parkingPersistencePort.findById(command.parkingId())
            .orElseThrow(() -> new ResourceNotFoundException("Parqueo", command.parkingId()));

      Zone zone = Zone.create(parking.getId(), command.name(), command.code(), command.address(),
            command.description(), command.latitude(), command.longitude(), command.cameraIds(),
            currentUserPort.requireCurrentUserId());
      ensureCodeAvailable(parking, zone.getCode());
      ensureNameAvailable(parking, zone.getName(), null);
      return toDto(zonePersistencePort.save(zone), parking);
   }

   // ========================= ACTUALIZAR =========================

   @Override
   public ZoneDto updateZone(Long zoneId, UpdateZoneCommand command) {
      Zone zone = findOrThrow(zoneId);
      Parking parking = parkingOf(zone);
      zone.update(command.name(), command.address(), command.description(),
            command.latitude(), command.longitude(), command.cameraIds());
      ensureNameAvailable(parking, zone.getName(), zone.getId());
      if (command.status() != null && !command.status().isBlank()) {
         zone.changeStatus(command.status());
      }
      zone.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(zonePersistencePort.save(zone), parking);
   }

   // ========================= ELIMINAR Y RESTAURAR =========================

   /** Eliminación lógica: sus espacios se conservan y vuelven con ella si se restaura */
   @Override
   public void deleteZone(Long zoneId) {
      Zone zone = findOrThrow(zoneId);
      long inside = transactionPersistencePort.countActiveByZoneId(zoneId);
      if (inside > 0) {
         throw new ResourceConflictException("No se puede eliminar la zona " + zone.getName()
               + ": tiene " + inside + (inside == 1 ? " vehículo dentro" : " vehículos dentro")
               + ". Regístralos como salidos o desactiva la zona para que no reciba más.");
      }
      zone.markAsDeleted(currentUserPort.requireCurrentUserId());
      zonePersistencePort.save(zone);
   }

   @Override
   public ZoneDto restoreZone(Long zoneId) {
      Zone zone = zonePersistencePort.findDeletedById(zoneId)
            .orElseThrow(() -> new ResourceNotFoundException("No hay una zona eliminada con ID: " + zoneId));
      Parking parking = parkingPersistencePort.findById(zone.getParkingId())
            .orElseThrow(() -> new ResourceConflictException("El parqueo de la zona " + zone.getName()
                  + " está eliminado. Restaura primero el parqueo."));
      // El nombre pudo ocuparse mientras estaba en la papelera (el código no: sigue reservado)
      ensureNameAvailable(parking, zone.getName(), zone.getId());
      zone.restore(currentUserPort.requireCurrentUserId());
      return toDto(zonePersistencePort.save(zone), parking);
   }

   // ========================= CONSULTAS =========================

   @Override
   @Transactional(readOnly = true)
   public ZoneDto getZoneById(Long zoneId) {
      Zone zone = findOrThrow(zoneId);
      return toDto(zone, parkingOf(zone));
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<ZoneDto> listAllZones(int pageNumber, int pageSize, String search, String status,
                                              Long parkingId, boolean deleted) {
      PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, "name", "ASC");
      PageResult<Zone> page = zonePersistencePort.findAll(pageRequest, search, normalizeStatusFilter(status),
            parkingId, deleted);
      Function<Zone, ZoneDto> mapper = batchMapper(page.content());
      return page.toPagedResponse(mapper);
   }

   @Override
   @Transactional(readOnly = true)
   public List<ZoneDto> listAllActiveZones(Long parkingId) {
      List<Zone> zones = zonePersistencePort.findAllActive(parkingId);
      return zones.stream().map(batchMapper(zones)).toList();
   }

   @Override
   @Transactional(readOnly = true)
   public List<ZoneDto> listZonesByParking(Long parkingId) {
      parkingPersistencePort.findById(parkingId)
            .orElseThrow(() -> new ResourceNotFoundException("Parqueo", parkingId));
      List<Zone> zones = zonePersistencePort.findByParkingId(parkingId);
      return zones.stream().map(batchMapper(zones)).toList();
   }

   // ========================= ESTADO =========================
   // Cambiar a inactiva o mantenimiento no expulsa a nadie: solo bloquea entradas nuevas.

   @Override
   public ZoneDto toggleZoneStatus(Long zoneId) {
      return changeStatus(zoneId, null);
   }

   @Override
   public ZoneDto activateZone(Long zoneId) {
      return changeStatus(zoneId, Zone.STATUS_ACTIVE);
   }

   @Override
   public ZoneDto deactivateZone(Long zoneId) {
      return changeStatus(zoneId, Zone.STATUS_INACTIVE);
   }

   @Override
   public ZoneDto setInMaintenance(Long zoneId) {
      return changeStatus(zoneId, Zone.STATUS_MAINTENANCE);
   }

   @Override
   public ZoneDto setOutOfService(Long zoneId) {
      return changeStatus(zoneId, Zone.STATUS_OUT_OF_SERVICE);
   }

   /** @param status null = alternar entre activa e inactiva */
   private ZoneDto changeStatus(Long zoneId, String status) {
      Zone zone = findOrThrow(zoneId);
      if (status == null) {
         zone.changeStatus(zone.isActive() ? Zone.STATUS_INACTIVE : Zone.STATUS_ACTIVE);
      } else {
         zone.changeStatus(status);
      }
      zone.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(zonePersistencePort.save(zone), parkingOf(zone));
   }

   // ========================= AUXILIARES =========================

   private Zone findOrThrow(Long zoneId) {
      return zonePersistencePort.findById(zoneId)
            .orElseThrow(() -> new ResourceNotFoundException("Zona", zoneId));
   }

   private Parking parkingOf(Zone zone) {
      return parkingPersistencePort.findByIdIncludingDeleted(zone.getParkingId())
            .orElseThrow(() -> new ResourceNotFoundException("Parqueo", zone.getParkingId()));
   }

   private void ensureCodeAvailable(Parking parking, String code) {
      zonePersistencePort.findByParkingIdAndCodeIncludingDeleted(parking.getId(), code).ifPresent(existing -> {
         if (existing.isDeleted()) {
            throw new ResourceConflictException("El código " + code + " pertenece a la zona eliminada "
                  + existing.getName() + " del parqueo " + parking.getName()
                  + ". Restáurala desde la papelera o usa otro código.");
         }
         throw new ResourceConflictException("El parqueo " + parking.getName() + " ya tiene una zona con el código " + code);
      });
   }

   /** @param selfId la propia zona al editar o restaurar (null al crear) */
   private void ensureNameAvailable(Parking parking, String name, Long selfId) {
      zonePersistencePort.findByParkingIdAndName(parking.getId(), name)
            .filter(existing -> !existing.getId().equals(selfId))
            .ifPresent(existing -> {
               throw new ResourceConflictException("El parqueo " + parking.getName()
                     + " ya tiene una zona llamada " + existing.getName());
            });
   }

   private static String normalizeStatusFilter(String status) {
      if (status == null || status.isBlank()) {
         return null;
      }
      String value = status.trim().toUpperCase();
      if (!Zone.STATUSES.contains(value)) {
         throw new ParkingDomainException("Estado de zona inválido: " + status);
      }
      return value;
   }

   /** Convierte una lista con 2 consultas en total: parqueos y capacidad de todas las zonas */
   private Function<Zone, ZoneDto> batchMapper(List<Zone> zones) {
      Map<Long, Parking> parkings = parkingPersistencePort
            .findAllByIdsIncludingDeleted(zones.stream().map(Zone::getParkingId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Parking::getId, p -> p));
      Map<Long, ZoneCapacity> capacities = capacityPort.capacityOfZones(zones.stream().map(Zone::getId).toList());
      return zone -> toDto(zone, parkings.get(zone.getParkingId()),
            capacities.getOrDefault(zone.getId(), ZoneCapacity.empty()));
   }

   private ZoneDto toDto(Zone zone, Parking parking) {
      ZoneCapacity capacity = capacityPort.capacityOfZones(List.of(zone.getId()))
            .getOrDefault(zone.getId(), ZoneCapacity.empty());
      return toDto(zone, parking, capacity);
   }

   private ZoneDto toDto(Zone z, Parking parking, ZoneCapacity c) {
      // Un espacio libre en una zona no activa no recibe vehículos: no cuenta como disponible
      int available = z.isActive() ? c.freeSpaces() : 0;
      int unavailable = Math.max(c.totalSpaces() - available - c.occupiedSpaces(), 0);
      double occupancy = c.totalSpaces() == 0 ? 0.0
            : Math.round(c.occupiedSpaces() * 1000.0 / c.totalSpaces()) / 10.0;
      boolean operational = z.isOperational() && parking != null && parking.isOperational();
      return new ZoneDto(
            z.getId(), z.getParkingId(),
            parking != null ? parking.getName() : null,
            parking != null ? parking.getCode() : null,
            z.getName(), z.getCode(), z.getAddress(), z.getDescription(),
            c.totalSpaces(), available, c.occupiedSpaces(), unavailable, occupancy,
            z.getLatitude(), z.getLongitude(),
            z.hasCameraMonitoring(), z.getCameraIdsList(), z.getCameraCount(),
            z.getStatus(), operational,
            z.getCreatedAt(), z.getCreatedBy(), z.getUpdatedAt(), z.getUpdatedBy(),
            z.getDeletedAt(), z.getDeletedBy()
      );
   }
}
