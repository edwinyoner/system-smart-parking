package com.winnersystems.smartparking.parking.application.port.input.parking;

import com.winnersystems.smartparking.parking.application.dto.query.ParkingDto;

/**
 * Puerto de entrada para sacar un parqueo de la papelera.
 */
public interface RestoreParkingUseCase {

   ParkingDto restoreParking(Long parkingId);
}
