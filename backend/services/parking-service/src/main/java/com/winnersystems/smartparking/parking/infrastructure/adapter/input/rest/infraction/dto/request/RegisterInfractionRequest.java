package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.infraction.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Las reglas finas (formato de placa, fechas, URL) las valida el dominio */
public record RegisterInfractionRequest(
      @NotNull(message = "El parqueo es obligatorio") Long parkingId,
      Long zoneId,
      Long spaceId,
      Long transactionId,
      @NotBlank(message = "La placa es obligatoria") @Size(max = 10) String plateNumber,
      @NotBlank(message = "El tipo es obligatorio") String infractionType,
      @NotBlank(message = "La gravedad es obligatoria") String severity,
      @NotBlank(message = "La descripción es obligatoria") @Size(min = 5, max = 1000) String description,
      @Size(max = 5, message = "Se admiten hasta 5 enlaces de evidencia") List<@Size(max = 500) String> evidence,
      @Positive(message = "La multa debe ser mayor que cero") @Digits(integer = 8, fraction = 2) BigDecimal fineAmount,
      LocalDate fineDueDate,
      LocalDateTime detectedAt,
      @Size(max = 500) String notes) {
}
