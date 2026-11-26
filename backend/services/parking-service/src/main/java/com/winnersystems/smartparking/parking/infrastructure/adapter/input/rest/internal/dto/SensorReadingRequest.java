package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Lectura estable de un sensor HC-SR04 enviada por iot-service */
public record SensorReadingRequest(
      @NotBlank(message = "Falta el sensor") @Size(max = 50) String sensorId,
      @NotNull(message = "Falta el estado") Boolean occupied
) {
}
