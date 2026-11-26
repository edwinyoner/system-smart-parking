package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * El operador confirma una lectura dudosa: la placa leída o la que corrigió mirando la foto.
 *
 * @param plateNumber    placa confirmada (obligatoria)
 * @param documentTypeId documento del conductor (opcional; lo pide el parqueo que exige documento)
 * @param documentNumber número de documento (opcional)
 */
public record ConfirmPlateReviewCommand(
      String plateNumber,
      Long documentTypeId,
      String documentNumber
) {
}
