package com.winnersystems.smartparking.parking.application.dto.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una estadía en el historial de un cliente o de un vehículo */
public record StayHistoryDto(
      Long id,
      String plateNumber,
      Long parkingId,
      String parkingName,
      String zoneName,
      String spaceCode,
      LocalDateTime entryTime,
      LocalDateTime exitTime,
      Integer durationMinutes,
      String status,
      String paymentStatus,
      String entryMethod,
      BigDecimal totalAmount,
      BigDecimal paidAmount,
      String currency) {
}
