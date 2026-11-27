package com.winnersystems.smartparking.parking.application.dto.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registrar una infracción. Quien registra sale del token.
 *
 * @param transactionId estadía relacionada (opcional): debe ser del mismo parqueo y de la misma placa;
 *                      si no se indica zona o espacio, se toman de la estadía
 * @param fineAmount    multa en PEN (opcional; se puede asignar después)
 * @param fineDueDate   fecha límite de pago (opcional; por defecto, unos días después)
 * @param detectedAt    cuándo ocurrió (opcional; por defecto, ahora)
 */
public record RegisterInfractionCommand(
      Long parkingId,
      Long zoneId,
      Long spaceId,
      Long transactionId,
      String plateNumber,
      String infractionType,
      String severity,
      String description,
      List<String> evidence,
      BigDecimal fineAmount,
      LocalDate fineDueDate,
      LocalDateTime detectedAt,
      String notes) {
}
