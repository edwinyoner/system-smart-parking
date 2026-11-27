package com.winnersystems.smartparking.parking.application.service.report;

import com.winnersystems.smartparking.parking.application.dto.query.*;
import com.winnersystems.smartparking.parking.application.dto.query.ReportDto.*;
import com.winnersystems.smartparking.parking.application.port.input.report.GetReportsUseCase;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Reportes de operación calculados desde las estadías (entrada y salida reales), no desde
 * contadores guardados. Diario: cada parqueo con sus zonas. Ocupación: hora por hora.
 *
 * La capacidad es la de hoy (los espacios no guardan historial). Las estadías canceladas fueron
 * errores de registro: solo se cuentan aparte.
 */
@Service
@Transactional(readOnly = true)
public class ReportService implements GetReportsUseCase {

   private final TransactionPersistencePort transactionPort;
   private final InfractionPersistencePort infractionPort;
   private final ParkingPersistencePort parkingPort;
   private final ZonePersistencePort zonePort;
   private final ParkingCapacityPort capacityPort;
   private final Clock clock;

   public ReportService(TransactionPersistencePort transactionPort, InfractionPersistencePort infractionPort,
                        ParkingPersistencePort parkingPort, ZonePersistencePort zonePort,
                        ParkingCapacityPort capacityPort, Clock clock) {
      this.transactionPort = transactionPort;
      this.infractionPort = infractionPort;
      this.parkingPort = parkingPort;
      this.zonePort = zonePort;
      this.capacityPort = capacityPort;
      this.clock = clock;
   }

   // ========================= DIARIO =========================

   @Override
   public DailyReport daily(LocalDate date, Long parkingId) {
      Day day = day(date);
      List<StayWindow> windows = transactionPort.findStayWindows(day.start, day.end, parkingId);
      List<InfractionPersistencePort.ZoneCount> infractions =
            infractionPort.countRegisteredBetween(day.start, day.end, parkingId);

      // Parqueos: los vigentes (aunque no hayan tenido movimiento) y los que tuvieron estadías
      List<Parking> parkings = parkingsOf(parkingId, windows);
      Map<Long, List<StayWindow>> byParking = windows.stream().collect(Collectors.groupingBy(StayWindow::parkingId));
      Map<Long, ParkingCapacity> capacities = capacityPort.capacityOf(parkings.stream().map(Parking::getId).toList());

      List<ParkingDay> result = new ArrayList<>();
      for (Parking p : parkings) {
         List<StayWindow> own = byParking.getOrDefault(p.getId(), List.of());
         List<Zone> zones = zonesOf(p.getId(), own);
         Map<Long, ZoneCapacity> zoneCapacities = capacityPort.capacityOfZones(zones.stream().map(Zone::getId).toList());

         List<ZoneDay> zoneDays = new ArrayList<>();
         for (Zone z : zones) {
            List<StayWindow> inZone = own.stream().filter(w -> z.getId().equals(w.zoneId())).toList();
            List<HourSlot> hours = HourlyOccupancy.compute(inZone, day.start, day.until);
            zoneDays.add(new ZoneDay(z.getId(), z.getName(), z.getCode(),
                  zoneCapacities.getOrDefault(z.getId(), ZoneCapacity.empty()).totalSpaces(),
                  summary(inZone, day, count(infractions, i -> p.getId().equals(i.parkingId())
                        && z.getId().equals(i.zoneId()))),
                  HourlyOccupancy.peakHour(hours), HourlyOccupancy.peakOccupied(hours)));
         }
         List<HourSlot> hours = HourlyOccupancy.compute(own, day.start, day.until);
         result.add(new ParkingDay(p.getId(), p.getName(), p.getCode(),
               capacities.getOrDefault(p.getId(), ParkingCapacity.empty()).totalSpaces(),
               summary(own, day, count(infractions, i -> p.getId().equals(i.parkingId()))),
               HourlyOccupancy.peakHour(hours), HourlyOccupancy.peakOccupied(hours), zoneDays));
      }
      return new DailyReport(date, day.until, summary(windows, day, count(infractions, i -> true)), result);
   }

   // ========================= OCUPACIÓN POR HORA =========================

   @Override
   public OccupancyReport occupancy(LocalDate date, Long parkingId, Long zoneId) {
      if (parkingId == null) {
         throw new ParkingDomainException("Elige un parqueo");
      }
      Day day = day(date);
      Parking parking = parkingPort.findByIdIncludingDeleted(parkingId)
            .orElseThrow(() -> new ResourceNotFoundException("Parqueo", parkingId));
      List<StayWindow> windows = transactionPort.findStayWindows(day.start, day.end, parkingId);
      List<Zone> zones = zonesOf(parkingId, windows);

      Zone zone = null;
      if (zoneId != null) {
         zone = zones.stream().filter(z -> z.getId().equals(zoneId)).findFirst()
               .orElseThrow(() -> new ResourceNotFoundException("La zona no pertenece a este parqueo"));
         windows = windows.stream().filter(w -> zoneId.equals(w.zoneId())).toList();
      }
      int capacity = zone != null
            ? capacityPort.capacityOfZones(List.of(zoneId)).getOrDefault(zoneId, ZoneCapacity.empty()).totalSpaces()
            : capacityPort.capacityOf(List.of(parkingId)).getOrDefault(parkingId, ParkingCapacity.empty()).totalSpaces();

      List<HourSlot> hours = HourlyOccupancy.compute(windows, day.start, day.until);
      DaySummary s = summary(windows, day, 0);
      return new OccupancyReport(date, day.until, parking.getId(), parking.getName(),
            zone != null ? zone.getId() : null, zone != null ? zone.getName() : null, capacity, hours,
            HourlyOccupancy.peakHour(hours), HourlyOccupancy.peakOccupied(hours), s.entries(), s.exits(), s.vehicles(),
            zones.stream().map(z -> new ZoneOption(z.getId(), z.getName(), z.getCode())).toList());
   }

   // ========================= CÁLCULOS =========================

   /** Un día: desde la medianoche hasta la siguiente; si es hoy, los datos llegan hasta ahora */
   private record Day(LocalDateTime start, LocalDateTime end, LocalDateTime until) {
   }

   private Day day(LocalDate date) {
      LocalDateTime now = LocalDateTime.now(clock);
      if (date == null) {
         date = now.toLocalDate();
      }
      if (date.isAfter(now.toLocalDate())) {
         throw new ParkingDomainException("La fecha no puede ser futura");
      }
      LocalDateTime start = date.atStartOfDay();
      LocalDateTime end = start.plusDays(1);
      return new Day(start, end, end.isAfter(now) ? now : end);
   }

   static DaySummary summary(List<StayWindow> windows, Day day, int infractions) {
      int entries = 0;
      int exits = 0;
      int stays = 0;
      int inside = 0;
      int cancelled = 0;
      long minutes = 0;
      Set<Long> vehicles = new HashSet<>();
      for (StayWindow w : windows) {
         boolean enteredToday = !w.entryTime().isBefore(day.start) && w.entryTime().isBefore(day.end);
         if (w.cancelled()) {
            if (enteredToday) {
               cancelled++;
            }
            continue;
         }
         if (enteredToday) {
            entries++;
         }
         if (w.exitTime() != null && !w.exitTime().isBefore(day.start) && w.exitTime().isBefore(day.end)) {
            exits++;
            minutes += Duration.between(w.entryTime(), w.exitTime()).toMinutes();
         }
         if (w.overlaps(day.start, day.until)) {
            stays++;
            vehicles.add(w.vehicleId());
         }
         // Al cierre del día (o ahora): entró antes y no había salido
         if (w.entryTime().isBefore(day.until) && (w.exitTime() == null || !w.exitTime().isBefore(day.until))) {
            inside++;
         }
      }
      Integer average = exits == 0 ? null : (int) Math.round((double) minutes / exits);
      return new DaySummary(entries, exits, vehicles.size(), stays, inside, cancelled, average, infractions);
   }

   private static int count(List<InfractionPersistencePort.ZoneCount> counts,
                            Predicate<InfractionPersistencePort.ZoneCount> filter) {
      return (int) counts.stream().filter(filter).mapToLong(InfractionPersistencePort.ZoneCount::count).sum();
   }

   private List<Parking> parkingsOf(Long parkingId, List<StayWindow> windows) {
      if (parkingId != null) {
         return List.of(parkingPort.findByIdIncludingDeleted(parkingId)
               .orElseThrow(() -> new ResourceNotFoundException("Parqueo", parkingId)));
      }
      Map<Long, Parking> byId = new LinkedHashMap<>();
      parkingPort.findAllActive().forEach(p -> byId.put(p.getId(), p));
      Set<Long> missing = windows.stream().map(StayWindow::parkingId).filter(id -> !byId.containsKey(id))
            .collect(Collectors.toSet());
      if (!missing.isEmpty()) {
         parkingPort.findAllByIdsIncludingDeleted(missing).forEach(p -> byId.put(p.getId(), p));
      }
      return byId.values().stream().sorted(Comparator.comparing(Parking::getName, String.CASE_INSENSITIVE_ORDER)).toList();
   }

   /** Zonas vigentes del parqueo más las eliminadas que tuvieron estadías ese día, por código */
   private List<Zone> zonesOf(Long parkingId, List<StayWindow> windows) {
      Map<Long, Zone> byId = new LinkedHashMap<>();
      zonePort.findByParkingId(parkingId).forEach(z -> byId.put(z.getId(), z));
      Set<Long> missing = windows.stream().map(StayWindow::zoneId)
            .filter(id -> id != null && !byId.containsKey(id)).collect(Collectors.toSet());
      if (!missing.isEmpty()) {
         zonePort.findAllByIdsIncludingDeleted(missing).forEach(z -> byId.put(z.getId(), z));
      }
      return byId.values().stream().sorted(Comparator.comparing(Zone::getCode, String.CASE_INSENSITIVE_ORDER)).toList();
   }
}
