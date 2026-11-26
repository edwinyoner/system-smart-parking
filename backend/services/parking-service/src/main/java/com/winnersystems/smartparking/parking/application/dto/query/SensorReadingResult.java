package com.winnersystems.smartparking.parking.application.dto.query;

/**
 * Qué hizo parking-service con la lectura de un sensor.
 *
 * @param outcome       SPACE_OCCUPIED, SPACE_RELEASED, STAY_MOVED o NO_CHANGE
 * @param transactionId estadía movida a este espacio (solo con STAY_MOVED)
 */
public record SensorReadingResult(
      String outcome,
      Long spaceId,
      String spaceCode,
      String spaceStatus,
      Long transactionId,
      String message
) {

   public static final String SPACE_OCCUPIED = "SPACE_OCCUPIED";
   public static final String SPACE_RELEASED = "SPACE_RELEASED";
   public static final String STAY_MOVED = "STAY_MOVED";
   public static final String NO_CHANGE = "NO_CHANGE";
}
