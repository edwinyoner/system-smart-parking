package com.winnersystems.smartparking.parking.application.port.input.zone;

import com.winnersystems.smartparking.parking.application.dto.query.ZoneDto;

/**
 * Puerto de entrada para sacar una zona de la papelera.
 */
public interface RestoreZoneUseCase {

   ZoneDto restoreZone(Long zoneId);
}
