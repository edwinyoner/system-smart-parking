package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDate;

/**
 * Filtros de la lista de infracciones (todos opcionales).
 *
 * @param status   OPEN (pendientes, vencidas incluidas), OVERDUE, RESOLVED, PAID, DISMISSED o WARNING
 * @param search   parte de la placa (con o sin guion) o del código
 * @param from     desde ese día (inclusive), por fecha de la infracción
 * @param to       hasta ese día (inclusive)
 */
public record InfractionFilter(
      String status,
      Long parkingId,
      String infractionType,
      String search,
      LocalDate from,
      LocalDate to) {

   public static final String OPEN = "OPEN";
   public static final String OVERDUE = "OVERDUE";
   public static final String RESOLVED = "RESOLVED";

   public static InfractionFilter none() {
      return new InfractionFilter(null, null, null, null, null, null);
   }
}
