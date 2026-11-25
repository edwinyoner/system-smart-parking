package com.winnersystems.smartparking.parking.application.dto.query;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de simular el cobro de una tarifa.
 *
 * @param fractionAmount precio de una fracción
 * @param rows           una fila por duración simulada
 */
public record RateSimulationDto(BigDecimal fractionAmount, List<Row> rows) {

   /**
    * @param minutes   estadía en minutos
    * @param fractions fracciones cobradas (0 = gratis)
    * @param amount    monto en PEN
    */
   public record Row(int minutes, int fractions, BigDecimal amount) {
   }
}
