package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal.dto;

import jakarta.validation.constraints.*;

/**
 * Lectura de cámara que envía iot-service (después de que ia-service leyó la placa).
 * La placa puede venir vacía o mal leída: entonces va a la cola de revisión.
 */
public record CameraCaptureRequest(
      @NotBlank(message = "Falta la captura") @Size(max = 64) String captureId,
      @NotBlank(message = "Falta la dirección") @Pattern(regexp = "(?i)ENTRY|EXIT", message = "Dirección: ENTRY o EXIT")
      String direction,
      @NotNull(message = "Falta el parqueo") Long parkingId,
      Long zoneId,
      @Size(max = 64) String deviceId,
      @Size(max = 20) String plateNumber,
      @NotNull(message = "Falta la confianza") @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
      @Size(max = 500) String photoUrl
) {
}
