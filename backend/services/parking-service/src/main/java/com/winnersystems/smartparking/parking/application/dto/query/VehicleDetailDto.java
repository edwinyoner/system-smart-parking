package com.winnersystems.smartparking.parking.application.dto.query;

import java.util.List;

/** Vehículo con sus conductores, la estadía en curso y los totales de su historial */
public record VehicleDetailDto(VehicleDto vehicle, List<LinkedDriver> drivers, StayHistoryDto activeStay,
                               HistoryTotalsDto totals) {

   public record LinkedDriver(Long customerId, String fullName, String documentTypeCode, String documentNumber,
                              int usageCount) {
   }
}
