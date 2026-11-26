package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal;

import com.winnersystems.smartparking.parking.application.dto.command.SensorReadingCommand;
import com.winnersystems.smartparking.parking.application.dto.query.SensorReadingResult;
import com.winnersystems.smartparking.parking.application.port.input.sensor.RecordSensorReadingUseCase;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal.dto.SensorReadingRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lecturas de los sensores HC-SR04, enviadas por iot-service por la red interna
 * (el gateway responde 404 a /internal/**). Exige el token de servicio con internal.sensors.process.
 */
@RestController
@RequestMapping("/internal/sensors")
public class InternalSensorRestAdapter {

   private final RecordSensorReadingUseCase useCase;

   public InternalSensorRestAdapter(RecordSensorReadingUseCase useCase) {
      this.useCase = useCase;
   }

   @PostMapping("/readings")
   @PreAuthorize("hasAuthority('SERVICE_CLIENT') and hasAuthority('internal.sensors.process')")
   public ResponseEntity<SensorReadingResult> record(@Valid @RequestBody SensorReadingRequest r) {
      return ResponseEntity.ok(useCase.recordReading(new SensorReadingCommand(r.sensorId(), r.occupied())));
   }
}
