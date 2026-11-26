package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;

/**
 * Lectura dudosa para la vista "Por revisar" del panel.
 *
 * @param suggestedPlate placa leída ya normalizada, o null si no tiene formato válido
 * @param transactionId  estadía creada o cerrada al confirmar
 */
public record PlateReviewDto(
      Long id,
      String captureId,
      String direction,
      Long parkingId,
      String parkingName,
      Long zoneId,
      String zoneName,
      String deviceId,
      String detectedPlate,
      String suggestedPlate,
      Double confidence,
      String photoUrl,
      String status,
      String confirmedPlate,
      Long transactionId,
      String discardReason,
      Long resolvedBy,
      LocalDateTime resolvedAt,
      LocalDateTime createdAt
) {
}
