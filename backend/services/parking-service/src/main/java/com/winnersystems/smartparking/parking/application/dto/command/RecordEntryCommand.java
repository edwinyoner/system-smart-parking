package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * Entrada MANUAL registrada por un operador desde el panel.
 *
 * El operador y el método no vienen aquí: el servicio toma al usuario del token y el método
 * es siempre MANUAL (así nadie registra a nombre de otro ni hace pasar una entrada manual
 * por una de cámara). La entrada por cámara usa CameraCaptureCommand.
 *
 * @param plateNumber       placa (se normaliza a ABC-123)
 * @param parkingId         parqueo
 * @param zoneId            zona
 * @param spaceId           espacio (opcional: si falta, se toma uno libre al azar de la zona)
 * @param documentTypeId    tipo de documento del conductor (opcional, va con el número)
 * @param documentNumber    número de documento (opcional)
 * @param customerFirstName nombre del conductor (opcional, solo con documento)
 * @param customerLastName  apellido (opcional)
 * @param customerPhone     teléfono (opcional)
 * @param customerEmail     correo (opcional)
 * @param notes             observaciones (opcional)
 */
public record RecordEntryCommand(
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
      String notes
) {
   public RecordEntryCommand {
      if (parkingId == null) {
         throw new IllegalArgumentException("Indica el parqueo");
      }
      if (zoneId == null) {
         throw new IllegalArgumentException("Indica la zona");
      }
   }

   /** true si se indicó documento (tipo o número): el dominio exige que vengan juntos */
   public boolean hasDocument() {
      return documentTypeId != null || (documentNumber != null && !documentNumber.isBlank());
   }
}
