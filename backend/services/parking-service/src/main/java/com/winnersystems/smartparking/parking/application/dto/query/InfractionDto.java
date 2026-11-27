package com.winnersystems.smartparking.parking.application.dto.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Infracción con los nombres de su parqueo, zona, espacio y conductor.
 *
 * @param status        estado guardado: PENDING o RESOLVED
 * @param displayStatus lo que ve el usuario: PENDING, OVERDUE, PAID, DISMISSED o WARNING
 * @param amountDue     lo que falta pagar de la multa
 * @param payable       se puede cobrar ahora
 * @param editable      se pueden cambiar sus datos o su multa (pendiente y sin pagos)
 */
public record InfractionDto(
      Long id,
      String infractionCode,
      Long parkingId,
      String parkingName,
      String parkingCode,
      Long zoneId,
      String zoneName,
      String zoneCode,
      Long spaceId,
      String spaceCode,
      Long transactionId,
      Long vehicleId,
      String plateNumber,
      Long customerId,
      String customerName,
      String infractionType,
      String severity,
      String description,
      List<String> evidence,
      LocalDateTime detectedAt,
      Long detectedBy,
      String detectedByName,
      String detectionMethod,
      BigDecimal fineAmount,
      String currency,
      LocalDate fineDueDate,
      BigDecimal paidAmount,
      BigDecimal amountDue,
      boolean finePaid,
      LocalDateTime finePaidAt,
      String paymentReference,
      String status,
      String displayStatus,
      boolean overdue,
      boolean payable,
      boolean editable,
      String resolutionType,
      String resolution,
      LocalDateTime resolvedAt,
      Long resolvedBy,
      String notes,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy) {
}
