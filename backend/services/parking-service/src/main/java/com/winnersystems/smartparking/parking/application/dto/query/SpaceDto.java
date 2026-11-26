package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;

/**
 * Espacio con los datos de su zona y parqueo.
 *
 * @param receivesVehicles libre y con su zona y parqueo activos: se puede asignar en una entrada
 */
public record SpaceDto(
      Long id,
      Long zoneId,
      String zoneName,
      String zoneCode,
      Long parkingId,
      String parkingName,
      String type,
      String typeName,
      String code,
      String description,
      Double width,
      Double length,
      Double area,
      boolean hasSensor,
      String sensorId,
      boolean hasCameraCoverage,
      String status,
      boolean receivesVehicles,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy,
      LocalDateTime deletedAt,
      Long deletedBy
) {
}
