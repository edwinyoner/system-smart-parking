package com.winnersystems.smartparking.parking.application.service.history;

import com.winnersystems.smartparking.parking.application.dto.query.HistoryTotalsDto;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.StayHistoryDto;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Convierte estadías en filas de historial, con los nombres de parqueo, zona, espacio y placa
 * consultados una sola vez por página (incluye eliminados: es historial).
 */
@Component
public class StayHistoryAssembler {

   private final TransactionPersistencePort transactionPort;
   private final InfractionPersistencePort infractionPort;
   private final ParkingPersistencePort parkingPort;
   private final ZonePersistencePort zonePort;
   private final SpacePersistencePort spacePort;
   private final VehiclePersistencePort vehiclePort;

   public StayHistoryAssembler(TransactionPersistencePort transactionPort,
                               InfractionPersistencePort infractionPort,
                               ParkingPersistencePort parkingPort,
                               ZonePersistencePort zonePort,
                               SpacePersistencePort spacePort,
                               VehiclePersistencePort vehiclePort) {
      this.transactionPort = transactionPort;
      this.infractionPort = infractionPort;
      this.parkingPort = parkingPort;
      this.zonePort = zonePort;
      this.spacePort = spacePort;
      this.vehiclePort = vehiclePort;
   }

   public PageResult<StayHistoryDto> page(PageResult<Transaction> page) {
      return new PageResult<>(toDtos(page.content()), page.pageNumber(), page.pageSize(), page.totalElements());
   }

   public StayHistoryDto one(Transaction t) {
      return toDtos(List.of(t)).get(0);
   }

   /** Totales de estadías e infracciones de un cliente o de un vehículo */
   public HistoryTotalsDto totals(Long customerId, Long vehicleId) {
      TransactionPersistencePort.HistoryTotals stays = transactionPort.historyTotals(customerId, vehicleId);
      TransactionPersistencePort.HistoryTotals fines = infractionPort.historyTotals(customerId, vehicleId);
      return new HistoryTotalsDto(stays.count(), orZero(stays.amount()), fines.count(), orZero(fines.amount()));
   }

   private List<StayHistoryDto> toDtos(List<Transaction> list) {
      if (list.isEmpty()) {
         return List.of();
      }
      Map<Long, Parking> parkings = parkingPort.findAllByIdsIncludingDeleted(ids(list, Transaction::getParkingId))
            .stream().collect(Collectors.toMap(Parking::getId, Function.identity(), (a, b) -> a));
      Map<Long, Zone> zones = zonePort.findAllByIdsIncludingDeleted(ids(list, Transaction::getZoneId))
            .stream().collect(Collectors.toMap(Zone::getId, Function.identity(), (a, b) -> a));
      Map<Long, Optional<Space>> spaces = new HashMap<>();
      Map<Long, Optional<Vehicle>> vehicles = new HashMap<>();

      return list.stream().map(t -> {
         Parking p = parkings.get(t.getParkingId());
         Zone z = zones.get(t.getZoneId());
         Space s = t.getSpaceId() == null ? null
               : spaces.computeIfAbsent(t.getSpaceId(), spacePort::findByIdIncludingDeleted).orElse(null);
         Vehicle v = vehicles.computeIfAbsent(t.getVehicleId(), vehiclePort::findById).orElse(null);
         return new StayHistoryDto(t.getId(), v != null ? v.getLicensePlate() : null,
               t.getParkingId(), p != null ? p.getName() : null, z != null ? z.getName() : null,
               s != null ? s.getCode() : null,
               t.getEntryTime(), t.getExitTime(), t.getDurationMinutes(), t.getStatus(), t.getPaymentStatus(),
               t.getEntryMethod(), t.getTotalAmount(), orZero(t.getPaidAmount()), t.getCurrency());
      }).toList();
   }

   private static Set<Long> ids(List<Transaction> list, Function<Transaction, Long> getter) {
      return list.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
   }

   private static BigDecimal orZero(BigDecimal value) {
      return value == null ? BigDecimal.ZERO : value;
   }
}
