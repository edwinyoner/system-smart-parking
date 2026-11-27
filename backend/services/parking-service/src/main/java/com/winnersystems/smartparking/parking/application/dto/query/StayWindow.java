package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;

/**
 * Estadía reducida a lo que necesitan los reportes: dónde estuvo, qué vehículo y entre qué horas.
 *
 * @param exitTime null si sigue dentro (ACTIVE) o si se canceló (CANCELLED no tiene salida)
 */
public record StayWindow(
      Long id,
      Long parkingId,
      Long zoneId,
      Long vehicleId,
      LocalDateTime entryTime,
      LocalDateTime exitTime,
      String status
) {

   public boolean cancelled() {
      return "CANCELLED".equals(status);
   }

   /** Si estuvo dentro en algún momento de [from, to). Una cancelada no cuenta: fue un error */
   public boolean overlaps(LocalDateTime from, LocalDateTime to) {
      return !cancelled() && entryTime.isBefore(to) && (exitTime == null || exitTime.isAfter(from));
   }

   /** Si estaba dentro en el instante t (entró antes o en t y aún no salía) */
   public boolean insideAt(LocalDateTime t) {
      return !cancelled() && !entryTime.isAfter(t) && (exitTime == null || exitTime.isAfter(t));
   }
}
