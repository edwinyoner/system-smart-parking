package com.winnersystems.smartparking.parking.application.service.space;

import com.winnersystems.smartparking.parking.application.dto.command.CreateSpaceCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateSpaceCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.SpaceDto;
import com.winnersystems.smartparking.parking.application.port.input.space.*;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Space;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Casos de uso de Espacios.
 *
 * Reglas que aplica este servicio (las propias del espacio están en la entidad):
 * - Se crea dentro de una zona vigente (404 si no existe o está eliminada).
 * - Código único dentro de la zona, también frente a los eliminados (409).
 * - Un sensor se asigna a un solo espacio vigente (409).
 * - La creación por lote es todo o nada, con un máximo de MAX_BATCH espacios.
 * - Liberar a mano un espacio con un vehículo registrado dentro no se permite (409):
 *   se libera al registrar la salida.
 * - Solo se restaura si su zona sigue vigente (409).
 */
@Service
@Transactional
public class SpaceService implements
      CreateSpaceUseCase,
      UpdateSpaceUseCase,
      DeleteSpaceUseCase,
      RestoreSpaceUseCase,
      GetSpaceUseCase,
      ListSpacesUseCase,
      ToggleSpaceStatusUseCase {

   /** Espacios por lote: suficiente para un parqueo grande, sin peticiones gigantes */
   public static final int MAX_BATCH = 500;

   private final SpacePersistencePort spacePersistencePort;
   private final ZonePersistencePort zonePersistencePort;
   private final ParkingPersistencePort parkingPersistencePort;
   private final TransactionPersistencePort transactionPersistencePort;
   private final CurrentUserPort currentUserPort;

   public SpaceService(SpacePersistencePort spacePersistencePort,
                       ZonePersistencePort zonePersistencePort,
                       ParkingPersistencePort parkingPersistencePort,
                       TransactionPersistencePort transactionPersistencePort,
                       CurrentUserPort currentUserPort) {
      this.spacePersistencePort = spacePersistencePort;
      this.zonePersistencePort = zonePersistencePort;
      this.parkingPersistencePort = parkingPersistencePort;
      this.transactionPersistencePort = transactionPersistencePort;
      this.currentUserPort = currentUserPort;
   }

   // ========================= CREAR =========================

   @Override
   public SpaceDto createSpace(CreateSpaceCommand command) {
      return createSpaces(List.of(command)).get(0);
   }

   @Override
   public List<SpaceDto> createSpaces(List<CreateSpaceCommand> commands) {
      if (commands == null || commands.isEmpty()) {
         throw new ParkingDomainException("Indica al menos un espacio");
      }
      if (commands.size() > MAX_BATCH) {
         throw new ParkingDomainException("Se pueden crear como máximo " + MAX_BATCH + " espacios por vez");
      }
      Long userId = currentUserPort.requireCurrentUserId();

      // 1. Validar cada espacio en el dominio y cargar sus zonas (una vez por zona)
      Map<Long, Zone> zones = new HashMap<>();
      List<Space> spaces = new ArrayList<>();
      for (CreateSpaceCommand c : commands) {
         if (c.zoneId() == null) {
            throw new ParkingDomainException("El espacio debe pertenecer a una zona");
         }
         zones.computeIfAbsent(c.zoneId(), id -> zonePersistencePort.findById(id)
               .orElseThrow(() -> new ResourceNotFoundException("Zona", id)));
         spaces.add(Space.create(c.zoneId(), c.code(), c.type(), c.description(), c.width(), c.length(),
               c.sensorId(), c.hasCameraCoverage(), userId));
      }

      // 2. Códigos repetidos dentro del lote o ya existentes en la zona
      Map<Long, List<String>> codesByZone = spaces.stream().collect(Collectors.groupingBy(
            Space::getZoneId, LinkedHashMap::new, Collectors.mapping(Space::getCode, Collectors.toList())));
      codesByZone.forEach((zoneId, codes) -> {
         Zone zone = zones.get(zoneId);
         List<String> repeated = duplicates(codes);
         if (!repeated.isEmpty()) {
            throw new ParkingDomainException("Códigos repetidos en la zona " + zone.getName() + ": " + preview(repeated));
         }
         Set<String> existing = spacePersistencePort.findExistingCodes(zoneId, codes);
         if (!existing.isEmpty()) {
            throw new ResourceConflictException("La zona " + zone.getName() + " ya tiene (o tiene en la papelera) "
                  + "los códigos: " + preview(new ArrayList<>(new TreeSet<>(existing))));
         }
      });

      // 3. Un sensor por espacio
      List<String> sensors = spaces.stream().map(Space::getSensorId).filter(Objects::nonNull).toList();
      ensureSensorsFree(sensors, null);

      // 4. Guardar todos juntos (la transacción deshace todo si algo falla)
      List<Space> saved = spacePersistencePort.saveAll(spaces);
      return saved.stream().map(batchMapper(saved)).toList();
   }

   // ========================= ACTUALIZAR =========================

   @Override
   public SpaceDto updateSpace(Long spaceId, UpdateSpaceCommand command) {
      Space space = findOrThrow(spaceId);
      space.update(command.type(), command.description(), command.width(), command.length(),
            command.sensorId(), command.hasCameraCoverage());
      if (space.getSensorId() != null) {
         ensureSensorsFree(List.of(space.getSensorId()), space.getId());
      }
      if (command.status() != null && !command.status().isBlank()
            && !command.status().trim().equalsIgnoreCase(space.getStatus())) {
         space.changeStatus(command.status());
      }
      space.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(spacePersistencePort.save(space));
   }

   // ========================= ELIMINAR Y RESTAURAR =========================

   @Override
   public void deleteSpace(Long spaceId) {
      Space space = findOrThrow(spaceId);
      if (space.isOccupied() || transactionPersistencePort.existsActiveBySpaceId(spaceId)) {
         throw new ResourceConflictException("El espacio " + space.getCode()
               + " tiene un vehículo dentro: registra su salida antes de eliminarlo");
      }
      space.markAsDeleted(currentUserPort.requireCurrentUserId());
      spacePersistencePort.save(space);
   }

   @Override
   public SpaceDto restoreSpace(Long spaceId) {
      Space space = spacePersistencePort.findDeletedById(spaceId)
            .orElseThrow(() -> new ResourceNotFoundException("No hay un espacio eliminado con ID: " + spaceId));
      zonePersistencePort.findById(space.getZoneId())
            .orElseThrow(() -> new ResourceConflictException("La zona del espacio " + space.getCode()
                  + " (o su parqueo) está eliminada. Restaura primero la zona."));
      // El sensor pudo asignarse a otro espacio mientras este estaba en la papelera
      if (space.getSensorId() != null) {
         ensureSensorsFree(List.of(space.getSensorId()), space.getId());
      }
      space.restore(currentUserPort.requireCurrentUserId());
      return toDto(spacePersistencePort.save(space));
   }

   // ========================= CONSULTAS =========================

   @Override
   @Transactional(readOnly = true)
   public SpaceDto getSpaceById(Long spaceId) {
      return toDto(findOrThrow(spaceId));
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<SpaceDto> listAllSpaces(int pageNumber, int pageSize, String search, String status,
                                                String type, Long zoneId, Long parkingId, boolean deleted) {
      PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, "code", "ASC");
      PageResult<Space> page = spacePersistencePort.findAll(pageRequest, search,
            normalizeFilter(status, Space.STATUSES, "Estado"), normalizeFilter(type, Space.TYPES, "Tipo"),
            zoneId, parkingId, deleted);
      return page.toPagedResponse(batchMapper(page.content()));
   }

   @Override
   @Transactional(readOnly = true)
   public List<SpaceDto> listSpacesByZone(Long zoneId) {
      zonePersistencePort.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zona", zoneId));
      List<Space> spaces = spacePersistencePort.findByZoneId(zoneId);
      return spaces.stream().map(batchMapper(spaces)).toList();
   }

   /** Una zona o parqueo no activos no reciben vehículos: la lista sale vacía */
   @Override
   @Transactional(readOnly = true)
   public List<SpaceDto> listAvailableByZone(Long zoneId) {
      Zone zone = zonePersistencePort.findById(zoneId)
            .orElseThrow(() -> new ResourceNotFoundException("Zona", zoneId));
      boolean parkingOperational = parkingPersistencePort.findById(zone.getParkingId())
            .map(Parking::isOperational).orElse(false);
      if (!zone.isOperational() || !parkingOperational) {
         return List.of();
      }
      List<Space> spaces = spacePersistencePort.findAvailableByZoneId(zoneId);
      return spaces.stream().map(batchMapper(spaces)).toList();
   }

   // ========================= ESTADO =========================

   @Override
   public SpaceDto toggleSpaceStatus(Long spaceId) {
      Space space = findOrThrow(spaceId);
      space.changeStatus(space.isAvailable() ? Space.STATUS_OUT_OF_SERVICE : Space.STATUS_AVAILABLE);
      return saveByUser(space);
   }

   @Override
   public SpaceDto markAsOccupied(Long spaceId) {
      Space space = findOrThrow(spaceId);
      space.occupy();
      return saveByUser(space);
   }

   @Override
   public SpaceDto markAsAvailable(Long spaceId) {
      Space space = findOrThrow(spaceId);
      if (transactionPersistencePort.existsActiveBySpaceId(spaceId)) {
         throw new ResourceConflictException("El espacio " + space.getCode()
               + " tiene un vehículo registrado dentro: se libera al registrar su salida");
      }
      if (space.isOccupied()) {
         space.release();
      } else {
         space.enable();
      }
      return saveByUser(space);
   }

   @Override
   public SpaceDto setInMaintenance(Long spaceId) {
      Space space = findOrThrow(spaceId);
      space.setInMaintenance();
      return saveByUser(space);
   }

   @Override
   public SpaceDto setOutOfService(Long spaceId) {
      Space space = findOrThrow(spaceId);
      space.setOutOfService();
      return saveByUser(space);
   }

   private SpaceDto saveByUser(Space space) {
      space.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(spacePersistencePort.save(space));
   }

   // ========================= AUXILIARES =========================

   private Space findOrThrow(Long spaceId) {
      return spacePersistencePort.findById(spaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Espacio", spaceId));
   }

   /** @param selfId el propio espacio al editar o restaurar (null al crear) */
   private void ensureSensorsFree(List<String> sensors, Long selfId) {
      if (sensors.isEmpty()) {
         return;
      }
      List<String> repeated = duplicates(sensors);
      if (!repeated.isEmpty()) {
         throw new ParkingDomainException("Sensores repetidos: " + preview(repeated));
      }
      spacePersistencePort.findBySensorIds(sensors).stream()
            .filter(other -> !other.getId().equals(selfId))
            .findFirst()
            .ifPresent(other -> {
               throw new ResourceConflictException("El sensor " + other.getSensorId()
                     + " ya está asignado al espacio " + other.getCode());
            });
   }

   private static List<String> duplicates(List<String> values) {
      Set<String> seen = new HashSet<>();
      return values.stream().filter(v -> !seen.add(v)).distinct().toList();
   }

   /** Muestra hasta 10 valores para que el mensaje no sea enorme */
   private static String preview(List<String> values) {
      String shown = String.join(", ", values.subList(0, Math.min(values.size(), 10)));
      return values.size() > 10 ? shown + " y " + (values.size() - 10) + " más" : shown;
   }

   private static String normalizeFilter(String value, Set<String> allowed, String label) {
      if (value == null || value.isBlank()) {
         return null;
      }
      String v = value.trim().toUpperCase();
      if (!allowed.contains(v)) {
         throw new ParkingDomainException(label + " de espacio inválido: " + value);
      }
      return v;
   }

   /** Convierte una lista con 2 consultas en total: zonas y parqueos */
   private Function<Space, SpaceDto> batchMapper(List<Space> spaces) {
      Map<Long, Zone> zones = zonePersistencePort
            .findAllByIdsIncludingDeleted(spaces.stream().map(Space::getZoneId).distinct().toList())
            .stream().collect(Collectors.toMap(Zone::getId, z -> z));
      Map<Long, Parking> parkings = parkingPersistencePort
            .findAllByIdsIncludingDeleted(zones.values().stream().map(Zone::getParkingId).distinct().toList())
            .stream().collect(Collectors.toMap(Parking::getId, p -> p));
      return space -> {
         Zone zone = zones.get(space.getZoneId());
         return toDto(space, zone, zone != null ? parkings.get(zone.getParkingId()) : null);
      };
   }

   private SpaceDto toDto(Space space) {
      return batchMapper(List.of(space)).apply(space);
   }

   private SpaceDto toDto(Space s, Zone zone, Parking parking) {
      boolean receives = s.isAvailableForOccupation()
            && zone != null && zone.isOperational()
            && parking != null && parking.isOperational();
      return new SpaceDto(
            s.getId(), s.getZoneId(),
            zone != null ? zone.getName() : null,
            zone != null ? zone.getCode() : null,
            zone != null ? zone.getParkingId() : null,
            parking != null ? parking.getName() : null,
            s.getType(), s.getTypeName(), s.getCode(), s.getDescription(),
            s.getWidth(), s.getLength(), s.getArea(),
            s.hasSensor(), s.getSensorId(), s.hasCameraMonitoring(),
            s.getStatus(), receives,
            s.getCreatedAt(), s.getCreatedBy(), s.getUpdatedAt(), s.getUpdatedBy(),
            s.getDeletedAt(), s.getDeletedBy()
      );
   }
}
