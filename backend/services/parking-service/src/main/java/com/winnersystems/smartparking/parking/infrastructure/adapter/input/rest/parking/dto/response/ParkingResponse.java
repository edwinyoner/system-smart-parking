package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.dto.response;

import java.time.LocalDateTime;

/**
 * Parqueo con su capacidad calculada en el momento desde los espacios reales.
 *
 * @param availableSpaces   espacios libres en zonas activas (los que reciben un vehículo ahora)
 * @param unavailableSpaces en mantenimiento, fuera de servicio o en zonas no activas
 * @param operational       activo y no eliminado: recibe vehículos
 */
public record ParkingResponse(
      Long id,
      String name,
      String code,
      String description,
      String address,
      Double latitude,
      Double longitude,
      Long managerId,
      String managerName,
      int totalZones,
      int totalSpaces,
      int availableSpaces,
      int occupiedSpaces,
      int unavailableSpaces,
      double occupancyPercentage,
      String status,
      boolean operational,
      boolean requireDriverDocument,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy,
      LocalDateTime deletedAt,
      Long deletedBy
) {
}
