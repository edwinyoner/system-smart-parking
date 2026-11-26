package com.winnersystems.smartparking.parking.application.port.input.occupancy;

import com.winnersystems.smartparking.parking.application.dto.query.OccupancyDto;

import java.util.List;

/**
 * Mapa de ocupación en tiempo real: parqueos, sus zonas y sus espacios por estado.
 */
public interface GetOccupancyUseCase {

   /** Parqueos vigentes (no eliminados) con su capacidad, ordenados por nombre */
   List<OccupancyDto.ParkingSummary> listParkings();

   /** Zonas y espacios de un parqueo. 404 si no existe o está eliminado */
   OccupancyDto.ParkingMap getParkingMap(Long parkingId);
}
