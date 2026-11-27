package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.infraction.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** fineAmount null quita la multa */
public record ChangeFineRequest(
      @Positive(message = "La multa debe ser mayor que cero") @Digits(integer = 8, fraction = 2) BigDecimal fineAmount,
      LocalDate dueDate) {
}
