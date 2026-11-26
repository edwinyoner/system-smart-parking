package com.winnersystems.smartparking.parking.application.service.transaction;

/**
 * Orden interna de salida (operador, cámara o revisión confirmada).
 *
 * @param transactionId     estadía (o se busca la activa por placa)
 * @param expectedParkingId parqueo de la cámara: si la estadía es de otro parqueo se rechaza
 * @param operatorId        null si la cámara registra sola; si viene, se exige que esté de turno
 */
public record ExitRequest(
      Long transactionId,
      String plateNumber,
      Long expectedParkingId,
      Long documentTypeId,
      String documentNumber,
      String method,
      Long operatorId,
      String captureId,
      String photoUrl,
      Double plateConfidence,
      String notes
) {
}
