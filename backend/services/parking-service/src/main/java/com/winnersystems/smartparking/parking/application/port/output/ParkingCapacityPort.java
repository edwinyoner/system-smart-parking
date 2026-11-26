package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.application.dto.query.ParkingCapacity;

import java.util.Collection;
import java.util.Map;

/**
 * Calcula la capacidad de los parqueos contando sus zonas y espacios reales.
 */
public interface ParkingCapacityPort {

   /**
    * Capacidad de varios parqueos en dos consultas (sin una consulta por parqueo).
    * Un parqueo sin zonas no aparece en el mapa: usar ParkingCapacity.empty().
    */
   Map<Long, ParkingCapacity> capacityOf(Collection<Long> parkingIds);
}
