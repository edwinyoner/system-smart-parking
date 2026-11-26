package com.winnersystems.smartparking.parking.application.dto.query;

/**
 * Capacidad de un parqueo calculada en el momento a partir de sus zonas y espacios
 * reales (no de contadores guardados).
 *
 * @param totalZones      zonas no eliminadas
 * @param totalSpaces     espacios no eliminados de esas zonas
 * @param availableSpaces espacios AVAILABLE en zonas ACTIVE: los que de verdad reciben un vehículo
 * @param occupiedSpaces  espacios OCCUPIED (en cualquier zona)
 */
public record ParkingCapacity(
      int totalZones,
      int totalSpaces,
      int availableSpaces,
      int occupiedSpaces
) {

   public static ParkingCapacity empty() {
      return new ParkingCapacity(0, 0, 0, 0);
   }

   /** Espacios que no reciben vehículos: en mantenimiento, fuera de servicio o en zonas no activas */
   public int unavailableSpaces() {
      return Math.max(totalSpaces - availableSpaces - occupiedSpaces, 0);
   }

   /** Porcentaje de ocupación sobre el total, con un decimal */
   public double occupancyPercentage() {
      if (totalSpaces == 0) {
         return 0.0;
      }
      return Math.round(occupiedSpaces * 1000.0 / totalSpaces) / 10.0;
   }
}
