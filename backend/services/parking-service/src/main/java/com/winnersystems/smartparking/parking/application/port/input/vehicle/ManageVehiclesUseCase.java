package com.winnersystems.smartparking.parking.application.port.input.vehicle;

import com.winnersystems.smartparking.parking.application.dto.query.*;

/** Consulta de vehículos e historial (de solo lectura: la placa no cambia) */
public interface ManageVehiclesUseCase {

   /** @param search parte de la placa, con o sin guion */
   PageResult<VehicleDto> list(String search, PageRequest pageRequest);

   VehicleDetailDto get(Long vehicleId);

   PageResult<StayHistoryDto> stays(Long vehicleId, PageRequest pageRequest);
}
