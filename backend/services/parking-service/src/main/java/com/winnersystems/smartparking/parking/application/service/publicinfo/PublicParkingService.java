package com.winnersystems.smartparking.parking.application.service.publicinfo;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingCapacity;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.Availability;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.ParkingCard;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.ParkingDetail;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.RateInfo;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.ScheduleItem;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.ShiftInfo;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.ZoneAvailability;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.SpaceCell;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.SpaceMap;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.ZoneSpaces;
import static com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.CELL_AVAILABLE;
import static com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.CELL_OCCUPIED;
import static com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto.CELL_UNAVAILABLE;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneCapacity;
import com.winnersystems.smartparking.parking.application.port.input.publicinfo.GetPublicParkingInfoUseCase;
import com.winnersystems.smartparking.parking.application.port.output.ParkingCapacityPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.SpacePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ZonePersistencePort;
import com.winnersystems.smartparking.parking.application.service.parkingshiftrate.ParkingScheduleResolver;
import com.winnersystems.smartparking.parking.application.service.parkingshiftrate.ParkingScheduleResolver.Resolved;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Rate;
import com.winnersystems.smartparking.parking.domain.model.Shift;
import com.winnersystems.smartparking.parking.domain.model.Space;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Información pública de los parqueos.
 *
 * Reglas:
 * - Un parqueo desactivado (INACTIVE) o eliminado no se publica (404). Uno en mantenimiento o
 *   fuera de servicio sí aparece, para que la app y el panel muestren que no recibe vehículos.
 * - Espacios libres = espacios AVAILABLE de zonas operativas (calculado, nunca un contador).
 * - Si el parqueo no está operativo o no atiende a esta hora (sin turno vigente), se
 *   publican 0 libres: así el panel no invita a entrar a un parqueo cerrado.
 */
@Service
@Transactional(readOnly = true)
public class PublicParkingService implements GetPublicParkingInfoUseCase {

   private static final int PAGE = 100;
   private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

   private final ParkingPersistencePort parkingPort;
   private final ZonePersistencePort zonePort;
   private final SpacePersistencePort spacePort;
   private final ParkingCapacityPort capacityPort;
   private final ParkingScheduleResolver scheduleResolver;
   private final Clock clock;

   public PublicParkingService(ParkingPersistencePort parkingPort,
                               ZonePersistencePort zonePort,
                               SpacePersistencePort spacePort,
                               ParkingCapacityPort capacityPort,
                               ParkingScheduleResolver scheduleResolver,
                               Clock clock) {
      this.parkingPort = parkingPort;
      this.zonePort = zonePort;
      this.spacePort = spacePort;
      this.capacityPort = capacityPort;
      this.scheduleResolver = scheduleResolver;
      this.clock = clock;
   }

   // ========================= LISTADO =========================

   @Override
   public List<ParkingCard> listParkings() {
      List<Parking> parkings = new ArrayList<>();
      int page = 0;
      PageResult<Parking> result;
      do {
         result = parkingPort.findAll(new PageRequest(page++, PAGE, "name", "ASC"), null, null, false);
         result.content().stream().filter(PublicParkingService::isPublic).forEach(parkings::add);
      } while (result.hasNext());

      Map<Long, ParkingCapacity> capacities = capacityPort.capacityOf(parkings.stream().map(Parking::getId).toList());
      LocalDateTime now = LocalDateTime.now(clock);
      return parkings.stream()
            .map(p -> toCard(p, capacities.getOrDefault(p.getId(), ParkingCapacity.empty()),
                  scheduleResolver.resolve(p.getId(), now)))
            .toList();
   }

   // ========================= DETALLE =========================

   @Override
   public ParkingDetail getParking(Long parkingId) {
      Parking parking = loadPublic(parkingId);
      LocalDateTime now = LocalDateTime.now(clock);
      Optional<Resolved> current = scheduleResolver.resolve(parkingId, now);
      ParkingCapacity capacity = capacityPort.capacityOf(List.of(parkingId))
            .getOrDefault(parkingId, ParkingCapacity.empty());

      List<ScheduleItem> schedule = scheduleResolver.usableConfigs(parkingId).stream()
            .sorted(Comparator.comparing((Resolved r) -> r.shift().getStartTime()))
            .map(r -> new ScheduleItem(toShift(r.shift()), toRate(r.rate())))
            .toList();

      return new ParkingDetail(
            toCard(parking, capacity, current),
            parking.getDescription(),
            zonesOf(parking, current.isPresent()),
            schedule,
            now);
   }

   // ========================= PANELES =========================

   @Override
   public Availability getAvailability(Long parkingId) {
      Parking parking = loadPublic(parkingId);
      LocalDateTime now = LocalDateTime.now(clock);
      boolean openNow = scheduleResolver.resolve(parkingId, now).isPresent();
      ParkingCapacity capacity = capacityPort.capacityOf(List.of(parkingId))
            .getOrDefault(parkingId, ParkingCapacity.empty());

      return new Availability(
            parking.getId(),
            parking.getCode(),
            parking.getName(),
            parking.getStatus(),
            parking.isOperational(),
            openNow,
            capacity.totalSpaces(),
            receives(parking, openNow) ? capacity.availableSpaces() : 0,
            capacity.occupiedSpaces(),
            capacity.availableSpaces() + capacity.occupiedSpaces(),
            zonesOf(parking, openNow),
            now);
   }

   // ========================= MAPA DE ESPACIOS =========================

   /**
    * Cuadrícula de espacios por zona. Ocupado se muestra siempre; libre solo si el espacio
    * recibe vehículos ahora (parqueo operativo y abierto, zona operativa); lo demás es
    * "no disponible". Así el mapa coincide con los libres del panel. Sin placas ni estadías.
    */
   @Override
   public SpaceMap getSpaceMap(Long parkingId) {
      Parking parking = loadPublic(parkingId);
      LocalDateTime now = LocalDateTime.now(clock);
      boolean openNow = scheduleResolver.resolve(parkingId, now).isPresent();
      boolean receives = receives(parking, openNow);

      Map<Long, List<Space>> byZone = spacePort.findByParkingId(parkingId).stream()
            .filter(s -> !s.isDeleted())
            .collect(Collectors.groupingBy(Space::getZoneId));

      List<ZoneSpaces> zones = zonePort.findByParkingId(parkingId).stream()
            .filter(z -> !z.isDeleted() && !z.isInactive())
            .sorted(Comparator.comparing(Zone::getCode))
            .map(z -> toZoneSpaces(z, byZone.getOrDefault(z.getId(), List.of()), receives && z.isOperational()))
            .toList();

      return new SpaceMap(parking.getId(), parking.getCode(), parking.getName(), parking.isOperational(), openNow,
            zones.stream().mapToInt(z -> z.spaces().size()).sum(),
            zones.stream().mapToInt(ZoneSpaces::available).sum(),
            zones, now);
   }

   private static ZoneSpaces toZoneSpaces(Zone zone, List<Space> spaces, boolean zoneReceives) {
      List<SpaceCell> cells = spaces.stream()
            .sorted(Comparator.comparing(Space::getCode))
            .map(s -> new SpaceCell(s.getCode(), s.getType(), publicStatus(s, zoneReceives)))
            .toList();
      int available = (int) cells.stream().filter(c -> CELL_AVAILABLE.equals(c.status())).count();
      int occupied = (int) cells.stream().filter(c -> CELL_OCCUPIED.equals(c.status())).count();
      return new ZoneSpaces(zone.getCode(), zone.getName(), zone.isOperational(),
            available, occupied, cells.size() - available - occupied, cells);
   }

   private static String publicStatus(Space space, boolean zoneReceives) {
      if (space.isOccupied()) return CELL_OCCUPIED;
      if (space.isAvailable() && zoneReceives) return CELL_AVAILABLE;
      return CELL_UNAVAILABLE;
   }

   // ========================= AUXILIARES =========================

   /** Desactivado o eliminado: no se publica */
   private static boolean isPublic(Parking parking) {
      return !parking.isDeleted() && !parking.isInactive();
   }

   private Parking loadPublic(Long parkingId) {
      return parkingPort.findById(parkingId)
            .filter(PublicParkingService::isPublic)
            .orElseThrow(() -> new ResourceNotFoundException("Parqueo no encontrado: " + parkingId));
   }

   /** Recibe vehículos ahora: operativo y con un turno vigente */
   private static boolean receives(Parking parking, boolean openNow) {
      return parking.isOperational() && openNow;
   }

   private ParkingCard toCard(Parking p, ParkingCapacity capacity, Optional<Resolved> current) {
      boolean openNow = current.isPresent();
      return new ParkingCard(
            p.getId(),
            p.getName(),
            p.getCode(),
            p.getAddress(),
            p.getLatitude(),
            p.getLongitude(),
            p.getStatus(),
            p.isOperational(),
            openNow,
            capacity.totalSpaces(),
            receives(p, openNow) ? capacity.availableSpaces() : 0,
            capacity.occupiedSpaces(),
            capacity.availableSpaces() + capacity.occupiedSpaces(),
            capacity.occupancyPercentage(),
            current.map(r -> toShift(r.shift())).orElse(null),
            current.map(r -> toRate(r.rate())).orElse(null));
   }

   private List<ZoneAvailability> zonesOf(Parking parking, boolean openNow) {
      List<Zone> zones = zonePort.findByParkingId(parking.getId()).stream()
            .filter(z -> !z.isDeleted() && !z.isInactive())
            .sorted(Comparator.comparing(Zone::getCode))
            .toList();
      Map<Long, ZoneCapacity> capacities = capacityPort.capacityOfZones(zones.stream().map(Zone::getId).toList());
      boolean receives = receives(parking, openNow);
      return zones.stream().map(z -> {
         ZoneCapacity c = capacities.getOrDefault(z.getId(), ZoneCapacity.empty());
         boolean zoneReceives = receives && z.isOperational();
         return new ZoneAvailability(z.getId(), z.getCode(), z.getName(), z.isOperational(),
               c.totalSpaces(), zoneReceives ? c.freeSpaces() : 0, c.occupiedSpaces(),
               c.freeSpaces() + c.occupiedSpaces());
      }).toList();
   }

   private static ShiftInfo toShift(Shift s) {
      return new ShiftInfo(s.getName(), s.getStartTime().format(HH_MM), s.getEndTime().format(HH_MM),
            s.crossesMidnight());
   }

   private static RateInfo toRate(Rate r) {
      return new RateInfo(r.getName(), r.getAmount(), r.getCurrency(), r.getBillingUnitMinutes(),
            r.getGraceMinutes(), r.getToleranceMinutes(), r.getMinimumMinutes());
   }
}
