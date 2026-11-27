package com.winnersystems.smartparking.parking.application.dto.query;

import java.math.BigDecimal;

/**
 * Totales del historial.
 *
 * @param totalPaid    lo pagado por sus estadías (neto de devoluciones)
 * @param pendingFines lo que falta cobrar de sus multas pendientes
 */
public record HistoryTotalsDto(long stays, BigDecimal totalPaid, long infractions, BigDecimal pendingFines) {
}
