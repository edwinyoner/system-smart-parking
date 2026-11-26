package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;

/**
 * Parqueo con su capacidad calculada desde los espacios reales.
 */
public record ParkingDto(
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
