package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;

/**
 * Vehículo (se crea solo, por placa, en la entrada o al registrar una infracción).
 *
 * @param driverCount  conductores distintos que entraron con documento en este vehículo
 * @param activeStayId estadía en curso (null si no está dentro)
 */
public record VehicleDto(
      Long id,
      String plateNumber,
      int totalVisits,
      long driverCount,
      Long activeStayId,
      LocalDateTime firstSeenDate,
      LocalDateTime lastSeenDate,
      LocalDateTime createdAt) {
}
