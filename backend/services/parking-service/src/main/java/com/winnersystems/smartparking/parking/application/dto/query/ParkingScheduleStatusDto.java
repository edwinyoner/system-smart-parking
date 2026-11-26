package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;

/**
 * Si el parqueo atiende en un momento dado y con qué turno y tarifa.
 *
 * @param open    true si hay un turno activo con tarifa activa a esa hora
 * @param current el turno y la tarifa que aplican (null si no atiende)
 */
public record ParkingScheduleStatusDto(Long parkingId, LocalDateTime at, boolean open, ParkingShiftRateDto current) {
}
