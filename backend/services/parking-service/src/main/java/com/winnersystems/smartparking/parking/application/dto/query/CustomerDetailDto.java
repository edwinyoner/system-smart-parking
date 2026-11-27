package com.winnersystems.smartparking.parking.application.dto.query;

import java.util.List;

/** Cliente con los vehículos que usó y los totales de su historial */
public record CustomerDetailDto(CustomerDto customer, List<LinkedVehicle> vehicles, HistoryTotalsDto totals) {

   /**
    * @param usageCount   veces que este cliente entró con este vehículo
    * @param activeStayId estadía en curso del vehículo (null si no está dentro)
    */
   public record LinkedVehicle(Long vehicleId, String plateNumber, int usageCount, Long activeStayId) {
   }
}
