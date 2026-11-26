package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking;

import com.winnersystems.smartparking.parking.application.dto.query.ParkingCapacity;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneCapacity;
import com.winnersystems.smartparking.parking.application.port.output.ParkingCapacityPort;
import com.winnersystems.smartparking.parking.domain.model.Space;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking.repository.ParkingRepository;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.repository.ZoneRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calcula la capacidad contando zonas y espacios reales (deuda técnica #1: antes se
 * guardaban contadores que nadie actualizaba).
 *
 * Disponible = espacio AVAILABLE dentro de una zona ACTIVE. Un espacio libre en una
 * zona en mantenimiento no recibe vehículos, así que no se cuenta como disponible.
 */
@Component
public class ParkingCapacityPersistenceAdapter implements ParkingCapacityPort {

   private final ParkingRepository repository;
   private final ZoneRepository zoneRepository;

   public ParkingCapacityPersistenceAdapter(ParkingRepository repository, ZoneRepository zoneRepository) {
      this.repository = repository;
      this.zoneRepository = zoneRepository;
   }

   @Override
   public Map<Long, ParkingCapacity> capacityOf(Collection<Long> parkingIds) {
      List<Long> ids = parkingIds == null ? List.of()
            : parkingIds.stream().filter(Objects::nonNull).distinct().toList();
      if (ids.isEmpty()) {
         return Map.of();
      }

      Map<Long, Counter> counters = new HashMap<>();
      for (Object[] row : repository.countZonesByParking(ids)) {
         counters.computeIfAbsent((Long) row[0], k -> new Counter()).zones = ((Number) row[1]).intValue();
      }
      for (Object[] row : repository.countSpacesByParking(ids)) {
         Counter c = counters.computeIfAbsent((Long) row[0], k -> new Counter());
         String spaceStatus = (String) row[1];
         String zoneStatus = (String) row[2];
         int count = ((Number) row[3]).intValue();
         c.spaces += count;
         if (Space.STATUS_OCCUPIED.equals(spaceStatus)) {
            c.occupied += count;
         } else if (Space.STATUS_AVAILABLE.equals(spaceStatus) && Zone.STATUS_ACTIVE.equals(zoneStatus)) {
            c.available += count;
         }
      }

      Map<Long, ParkingCapacity> result = new HashMap<>();
      counters.forEach((id, c) -> result.put(id, new ParkingCapacity(c.zones, c.spaces, c.available, c.occupied)));
      return result;
   }

   @Override
   public Map<Long, ZoneCapacity> capacityOfZones(Collection<Long> zoneIds) {
      List<Long> ids = zoneIds == null ? List.of()
            : zoneIds.stream().filter(Objects::nonNull).distinct().toList();
      if (ids.isEmpty()) {
         return Map.of();
      }
      Map<Long, Counter> counters = new HashMap<>();
      for (Object[] row : zoneRepository.countSpacesByZone(ids)) {
         Counter c = counters.computeIfAbsent((Long) row[0], k -> new Counter());
         String spaceStatus = (String) row[1];
         int count = ((Number) row[2]).intValue();
         c.spaces += count;
         if (Space.STATUS_OCCUPIED.equals(spaceStatus)) {
            c.occupied += count;
         } else if (Space.STATUS_AVAILABLE.equals(spaceStatus)) {
            c.available += count;
         }
      }
      Map<Long, ZoneCapacity> result = new HashMap<>();
      counters.forEach((id, c) -> result.put(id, new ZoneCapacity(c.spaces, c.available, c.occupied)));
      return result;
   }

   /** Acumulador temporal por parqueo o zona */
   private static final class Counter {
      int zones;
      int spaces;
      int available;
      int occupied;
   }
}
