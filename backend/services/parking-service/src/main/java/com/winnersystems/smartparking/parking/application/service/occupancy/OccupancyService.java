package com.winnersystems.smartparking.parking.application.service.occupancy;

import com.winnersystems.smartparking.parking.application.dto.query.ActiveStayView;
import com.winnersystems.smartparking.parking.application.dto.query.OccupancyDto;
import com.winnersystems.smartparking.parking.application.dto.query.OccupancyDto.ParkingMap;
import com.winnersystems.smartparking.parking.application.dto.query.OccupancyDto.ParkingSummary;
import com.winnersystems.smartparking.parking.application.dto.query.OccupancyDto.SpaceCell;
import com.winnersystems.smartparking.parking.application.dto.query.OccupancyDto.StatusCounts;
import com.winnersystems.smartparking.parking.application.dto.query.OccupancyDto.ZoneMap;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingCapacity;
import com.winnersystems.smartparking.parking.application.port.input.occupancy.GetOccupancyUseCase;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.OperatorAssignmentPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingCapacityPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.SpacePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ZonePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ActionNotAllowedException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.OperatorAssignment;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Space;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mapa de ocupación: se arma con tres consultas por parqueo (zonas, espacios y estadías en
 * curso), sin importar cuántas zonas tenga. Todo sale del estado real de cada espacio.
 *
 * Regla 8: cada usuario ve solo sus parqueos (los que gestiona como responsable y aquellos
 * donde tiene una asignación vigente hoy; si la asignación es de una zona, solo esa zona).
 * Con occupancy.view-all (ADMIN y AUTORIDAD) se ven todos.
 */
@Service
@Transactional(readOnly = true)
public class OccupancyService implements GetOccupancyUseCase {

   /** Permiso que deja ver qué vehículo ocupa cada espacio */
   static final String VIEW_VEHICLES = "transactions.view";
   /** Permiso para ver todos los parqueos, no solo los propios */
   static final String VIEW_ALL = "occupancy.view-all";
   private static final int PAGE = 100;

   private final ParkingPersistencePort parkingPort;
   private final ZonePersistencePort zonePort;
   private final SpacePersistencePort spacePort;
   private final TransactionPersistencePort transactionPort;
   private final ParkingCapacityPort capacityPort;
   private final OperatorAssignmentPersistencePort assignmentPort;
   private final CurrentUserPort currentUser;
   private final Clock clock;

   public OccupancyService(ParkingPersistencePort parkingPort,
                           ZonePersistencePort zonePort,
                           SpacePersistencePort spacePort,
                           TransactionPersistencePort transactionPort,
                           ParkingCapacityPort capacityPort,
                           OperatorAssignmentPersistencePort assignmentPort,
                           CurrentUserPort currentUser,
                           Clock clock) {
      this.parkingPort = parkingPort;
      this.zonePort = zonePort;
      this.spacePort = spacePort;
      this.transactionPort = transactionPort;
      this.capacityPort = capacityPort;
      this.assignmentPort = assignmentPort;
      this.currentUser = currentUser;
      this.clock = clock;
   }

   @Override
   public List<ParkingSummary> listParkings() {
      List<Parking> parkings = new ArrayList<>();
      int page = 0;
      PageResult<Parking> result;
      do {
         result = parkingPort.findAll(new PageRequest(page++, PAGE, "name", "ASC"), null, null, false);
         parkings.addAll(result.content());
      } while (result.hasNext());

      Access access = access();
      if (!access.all()) {
         parkings.removeIf(p -> !access.canSee(p));
      }

      Map<Long, ParkingCapacity> capacities = capacityPort.capacityOf(parkings.stream().map(Parking::getId).toList());
      return parkings.stream()
            .map(p -> {
               ParkingCapacity c = capacities.getOrDefault(p.getId(), ParkingCapacity.empty());
               return new ParkingSummary(p.getId(), p.getName(), p.getCode(), p.getStatus(), p.isOperational(),
                     c.totalZones(), c.totalSpaces(), c.availableSpaces(), c.occupiedSpaces(),
                     c.unavailableSpaces(), c.occupancyPercentage());
            })
            .toList();
   }

   @Override
   public ParkingMap getParkingMap(Long parkingId) {
      Parking parking = parkingPort.findById(parkingId)
            .orElseThrow(() -> new ResourceNotFoundException("Parqueo", parkingId));
      Access access = access();
      if (!access.canSee(parking)) {
         throw new ActionNotAllowedException("No tienes asignado el parqueo " + parking.getName());
      }

      List<Zone> zones = zonePort.findByParkingId(parkingId).stream()
            .filter(z -> access.canSeeZone(parking, z.getId()))
            .sorted(Comparator.comparing(Zone::getCode, String.CASE_INSENSITIVE_ORDER))
            .toList();
      Map<Long, List<Space>> spacesByZone = spacePort.findByParkingId(parkingId).stream()
            .collect(Collectors.groupingBy(Space::getZoneId));

      // Las placas solo para quien puede ver las estadías
      boolean showVehicles = currentUser.hasAuthority(VIEW_VEHICLES);
      Map<Long, ActiveStayView> stayBySpace = showVehicles
            ? transactionPort.findActiveStaysByParkingId(parkingId).stream()
                  .filter(s -> s.spaceId() != null)
                  .collect(Collectors.toMap(ActiveStayView::spaceId, Function.identity(), (a, b) -> a))
            : Map.of();

      List<ZoneMap> zoneMaps = new ArrayList<>();
      int total = 0, available = 0, occupied = 0, maintenance = 0, outOfService = 0, forEntry = 0;
      for (Zone zone : zones) {
         List<Space> spaces = spacesByZone.getOrDefault(zone.getId(), List.of());
         StatusCounts counts = count(spaces);
         boolean receives = parking.isOperational() && zone.isOperational();
         int zoneForEntry = receives ? counts.available() : 0;
         zoneMaps.add(new ZoneMap(zone.getId(), zone.getCode(), zone.getName(), zone.getStatus(), receives,
               counts, zoneForEntry, spaces.stream().map(s -> toCell(s, stayBySpace.get(s.getId()))).toList()));

         total += counts.total();
         available += counts.available();
         occupied += counts.occupied();
         maintenance += counts.maintenance();
         outOfService += counts.outOfService();
         forEntry += zoneForEntry;
      }

      return new ParkingMap(parking.getId(), parking.getName(), parking.getCode(), parking.getAddress(),
            parking.getStatus(), parking.isOperational(),
            new StatusCounts(total, available, occupied, maintenance, outOfService), forEntry,
            showVehicles, LocalDateTime.now(clock), zoneMaps);
   }

   // ========================= ALCANCE (REGLA 8) =========================

   /**
    * Qué puede ver el usuario. zonesByParking: null = todo el parqueo; un conjunto = solo esas zonas.
    */
   private record Access(boolean all, Long userId, Map<Long, Set<Long>> zonesByParking) {

      boolean canSee(Parking parking) {
         return all || parking.getId() != null && (zonesByParking.containsKey(parking.getId())
               || userId != null && userId.equals(parking.getManagerId()));
      }

      boolean canSeeZone(Parking parking, Long zoneId) {
         // Todos, o el responsable del parqueo: todas sus zonas
         if (all || userId != null && userId.equals(parking.getManagerId())
               || !zonesByParking.containsKey(parking.getId())) {
            return true;
         }
         Set<Long> zones = zonesByParking.get(parking.getId());
         return zones == null || zones.contains(zoneId);
      }
   }

   private Access access() {
      if (currentUser.hasAuthority(VIEW_ALL)) {
         return new Access(true, null, Map.of());
      }
      Long userId = currentUser.getCurrentUserId().orElse(null);
      Map<Long, Set<Long>> zones = new HashMap<>();
      if (userId != null) {
         LocalDate today = LocalDate.now(clock);
         for (OperatorAssignment a : assignmentPort.findAll(null, null, userId)) {
            if (!a.isActiveOn(today)) {
               continue;
            }
            if (a.isParkingWide()) {
               zones.put(a.getParkingId(), null);
            } else if (!zones.containsKey(a.getParkingId()) || zones.get(a.getParkingId()) != null) {
               zones.computeIfAbsent(a.getParkingId(), k -> new HashSet<>()).add(a.getZoneId());
            }
         }
      }
      return new Access(false, userId, zones);
   }

   private static StatusCounts count(List<Space> spaces) {
      Map<String, Long> byStatus = spaces.stream().collect(Collectors.groupingBy(Space::getStatus, Collectors.counting()));
      return new StatusCounts(spaces.size(),
            byStatus.getOrDefault(Space.STATUS_AVAILABLE, 0L).intValue(),
            byStatus.getOrDefault(Space.STATUS_OCCUPIED, 0L).intValue(),
            byStatus.getOrDefault(Space.STATUS_MAINTENANCE, 0L).intValue(),
            byStatus.getOrDefault(Space.STATUS_OUT_OF_SERVICE, 0L).intValue());
   }

   private static SpaceCell toCell(Space space, ActiveStayView stay) {
      return new SpaceCell(space.getId(), space.getCode(), space.getType(), space.getStatus(), space.hasSensor(),
            stay == null ? null : stay.transactionId(),
            stay == null ? null : stay.plateNumber(),
            stay == null ? null : stay.entryTime());
   }
}
