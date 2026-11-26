package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal;

import com.winnersystems.smartparking.parking.application.dto.command.CameraCaptureCommand;
import com.winnersystems.smartparking.parking.application.dto.query.CameraCaptureResult;
import com.winnersystems.smartparking.parking.application.port.input.transaction.ProcessCameraCaptureUseCase;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal.dto.CameraCaptureRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lecturas de las cámaras de entrada y salida, enviadas por iot-service por la red interna
 * (el gateway responde 404 a /internal/**). Exige el token de servicio con internal.captures.process.
 *
 * Responde siempre 200 con un resultado que el dispositivo entiende (abrir barrera, cobrar,
 * esperar al operador o rechazar); solo los datos mal formados dan 400.
 */
@RestController
@RequestMapping("/internal/captures")
public class InternalCaptureRestAdapter {

   private final ProcessCameraCaptureUseCase processCameraCaptureUseCase;

   public InternalCaptureRestAdapter(ProcessCameraCaptureUseCase processCameraCaptureUseCase) {
      this.processCameraCaptureUseCase = processCameraCaptureUseCase;
   }

   @PostMapping
   @PreAuthorize("hasAuthority('SERVICE_CLIENT') and hasAuthority('internal.captures.process')")
   public ResponseEntity<CameraCaptureResult> process(@Valid @RequestBody CameraCaptureRequest r) {
      return ResponseEntity.ok(processCameraCaptureUseCase.processCapture(new CameraCaptureCommand(
            r.captureId(), r.direction(), r.parkingId(), r.zoneId(), r.deviceId(),
            r.plateNumber(), r.confidence(), r.photoUrl())));
   }
}
