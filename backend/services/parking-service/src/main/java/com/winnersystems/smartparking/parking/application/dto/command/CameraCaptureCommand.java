package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * Lectura de una cámara de entrada o salida, enviada por iot-service después de que
 * ia-service leyó la placa. Llega con el token de servicio de iot-service, no de un usuario.
 *
 * @param captureId   captura de iot-service (única: si se reenvía, se responde lo mismo)
 * @param direction   ENTRY o EXIT
 * @param parkingId   parqueo de la cámara
 * @param zoneId      zona de la cámara (obligatoria en la entrada)
 * @param deviceId    identificador de la cámara (opcional)
 * @param plateNumber placa leída por la IA (puede venir vacía si no se leyó nada)
 * @param confidence  confianza de la lectura (0.0 - 1.0)
 * @param photoUrl    foto en el almacenamiento (opcional)
 */
public record CameraCaptureCommand(
      String captureId,
      String direction,
      Long parkingId,
      Long zoneId,
      String deviceId,
      String plateNumber,
      Double confidence,
      String photoUrl
) {
}
