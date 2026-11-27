package com.winnersystems.smartparking.parking.application.dto.query;

import java.math.BigDecimal;

/**
 * Conteos para la cabecera de la lista y el dashboard.
 *
 * @param open          pendientes (incluye las vencidas)
 * @param overdue       pendientes con multa vencida
 * @param amountPending lo que falta cobrar de las multas pendientes (PEN)
 */
public record InfractionSummaryDto(
      long open,
      long overdue,
      long paid,
      long dismissed,
      long warning,
      BigDecimal amountPending) {
}
