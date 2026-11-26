package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.application.dto.query.ParkingCapacity;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneCapacity;

import java.util.Collection;
import java.util.Map;

/**
 * Calcula la capacidad de parqueos y zonas contando sus espacios reales
 * (no hay contadores guardados que se puedan desfasar).
 */
public interface ParkingCapacityPort {

   /**
    * Capacidad de varios parqueos en dos consultas (sin una consulta por parqueo).
    * Un parqueo sin zonas no aparece en el mapa: usar ParkingCapacity.empty().
    */
   Map<Long, ParkingCapacity> capacityOf(Collection<Long> parkingIds);

   /**
    * Espacios de varias zonas en una consulta. Una zona sin espacios no aparece:
    * usar ZoneCapacity.empty().
    */
   Map<Long, ZoneCapacity> capacityOfZones(Collection<Long> zoneIds);
}
