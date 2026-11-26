package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * Salida MANUAL registrada por un operador. Como en la entrada, el operador sale del token.
 *
 * @param transactionId      estadía (o se busca la activa por placa)
 * @param plateNumber        placa, si no se indica la estadía
 * @param exitDocumentTypeId documento presentado (opcional; obligatorio solo si el parqueo
 *                           exige documento y se entró con documento)
 * @param exitDocumentNumber número presentado (opcional)
 * @param notes              observaciones (opcional)
 */
public record RecordExitCommand(
      Long transactionId,
      String plateNumber,
      Long exitDocumentTypeId,
      String exitDocumentNumber,
      String notes
) {
   public RecordExitCommand {
      if (transactionId == null && (plateNumber == null || plateNumber.isBlank())) {
         throw new IllegalArgumentException("Indica la estadía o la placa");
      }
   }

   public boolean hasDocument() {
      return exitDocumentTypeId != null || (exitDocumentNumber != null && !exitDocumentNumber.isBlank());
   }
}
