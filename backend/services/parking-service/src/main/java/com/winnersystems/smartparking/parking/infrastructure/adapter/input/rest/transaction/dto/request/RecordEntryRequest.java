package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrada manual desde el panel. El operador sale del token (no se envía) y el método es MANUAL.
 * Placa en cualquier forma ("abc 123", "ABC123"): el dominio la normaliza a ABC-123.
 * Documento y datos del conductor son opcionales (salvo que el parqueo exija documento).
 */
public record RecordEntryRequest(
      @NotBlank(message = "La placa es obligatoria")
      @Size(max = 10, message = "La placa no puede superar 10 caracteres")
      String plateNumber,

      @NotNull(message = "El parqueo es obligatorio")
      Long parkingId,

      @NotNull(message = "La zona es obligatoria")
      Long zoneId,

      // Opcional: si falta, se descuenta un espacio libre al azar de la zona
      Long spaceId,

      Long documentTypeId,

      @Size(max = 20, message = "El documento no puede superar 20 caracteres")
      String documentNumber,

      @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
      String customerFirstName,

      @Size(max = 100, message = "El apellido no puede superar 100 caracteres")
      String customerLastName,

      @Pattern(regexp = "^$|^9\\d{8}$", message = "El celular debe tener 9 dígitos y empezar con 9")
      String customerPhone,

      @Email(message = "El correo no es válido")
      @Size(max = 100, message = "El correo no puede superar 100 caracteres")
      String customerEmail,

      @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres")
      String notes
) {
}
