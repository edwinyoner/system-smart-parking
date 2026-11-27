package com.winnersystems.smartparking.parking.application.dto.command;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Poner, cambiar o quitar la multa.
 *
 * @param fineAmount null para quitarla
 * @param dueDate    fecha límite (opcional)
 */
public record ChangeInfractionFineCommand(BigDecimal fineAmount, LocalDate dueDate) {
}
