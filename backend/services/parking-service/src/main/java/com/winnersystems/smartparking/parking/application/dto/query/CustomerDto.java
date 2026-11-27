package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;

/** Cliente (conductor identificado con documento al entrar) */
public record CustomerDto(
      Long id,
      Long documentTypeId,
      String documentTypeCode,
      String documentNumber,
      String firstName,
      String lastName,
      String fullName,
      String phone,
      String email,
      String address,
      int totalVisits,
      long vehicleCount,
      LocalDateTime firstSeenDate,
      LocalDateTime lastSeenDate,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
}
