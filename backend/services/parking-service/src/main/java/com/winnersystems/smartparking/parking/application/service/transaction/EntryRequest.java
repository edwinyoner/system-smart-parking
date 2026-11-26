package com.winnersystems.smartparking.parking.application.service.transaction;

/**
 * Orden interna de entrada. La arman el operador (RecordEntryCommand), la cámara
 * (CameraCaptureCommand) y la confirmación de una revisión; las tres terminan en
 * TransactionService.enterVehicle con las mismas reglas.
 *
 * @param operatorId usuario que registra o confirma (null si la cámara registra sola);
 *                   si viene, se exige que esté de turno
 * @param method     MANUAL o CAMERA_AI
 */
public record EntryRequest(
      String plateNumber,
      Long parkingId,
      Long zoneId,
      Long spaceId,
      Long documentTypeId,
      String documentNumber,
      String customerFirstName,
      String customerLastName,
      String customerPhone,
      String customerEmail,
      String method,
      Long operatorId,
      String captureId,
      String photoUrl,
      Double plateConfidence,
      String notes
) {
}
