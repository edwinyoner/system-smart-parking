package com.winnersystems.smartparking.parking.application.dto.query;

/**
 * Espacios de una zona contados en el momento.
 *
 * @param totalSpaces    espacios no eliminados
 * @param freeSpaces     espacios en estado AVAILABLE (sin mirar el estado de la zona)
 * @param occupiedSpaces espacios en estado OCCUPIED
 */
public record ZoneCapacity(int totalSpaces, int freeSpaces, int occupiedSpaces) {

   public static ZoneCapacity empty() {
      return new ZoneCapacity(0, 0, 0);
   }
}
