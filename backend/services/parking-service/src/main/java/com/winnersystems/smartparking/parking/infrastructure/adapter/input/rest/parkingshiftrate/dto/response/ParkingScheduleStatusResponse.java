package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.response;

import java.time.LocalDateTime;

/**
 * Si el parqueo atiende ahora y con qué turno y tarifa (current es null si no atiende).
 */
public record ParkingScheduleStatusResponse(Long parkingId, LocalDateTime at, boolean open,
                                            ParkingShiftRateResponse current) {
}
