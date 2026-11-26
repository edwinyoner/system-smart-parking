package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Zona con su capacidad calculada desde los espacios reales.
 *
 * @param availableSpaces   espacios libres que reciben un vehículo ahora (0 si la zona no está activa)
 * @param unavailableSpaces en mantenimiento, fuera de servicio, o libres en una zona no activa
 * @param operational       la zona y su parqueo están activos: recibe vehículos
 */
public record ZoneDto(
      Long id,
      Long parkingId,
      String parkingName,
      String parkingCode,
      String name,
      String code,
      String address,
      String description,
      int totalSpaces,
      int availableSpaces,
      int occupiedSpaces,
      int unavailableSpaces,
      double occupancyPercentage,
      Double latitude,
      Double longitude,
      boolean hasCamera,
      List<String> cameraIds,
      int cameraCount,
      String status,
      boolean operational,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy,
      LocalDateTime deletedAt,
      Long deletedBy
) {
}
