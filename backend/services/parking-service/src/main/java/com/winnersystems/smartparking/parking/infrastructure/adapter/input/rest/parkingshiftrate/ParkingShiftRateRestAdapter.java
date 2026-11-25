package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate;

import com.winnersystems.smartparking.parking.application.dto.command.ConfigureParkingShiftRatesCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingShiftRateDto;
import com.winnersystems.smartparking.parking.application.port.input.parkingshiftrate.ConfigureParkingShiftRatesUseCase;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.request.ConfigureParkingShiftRatesRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.response.ParkingShiftRateResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.mapper.ParkingShiftRateRestMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Adapter para configuración de tarifas por turno en parqueos.
 * Esta configuración se aplica a TODAS las zonas del parqueo.
 *
 * Endpoints:
 * - POST   /parkings/{parkingId}/shift-rates → Configurar tarifas (Paso 4 del Stepper)
 * - GET    /parkings/{parkingId}/shift-rates → Obtener configuraciones
 * - DELETE /parkings/shift-rates/{configId}  → Eliminar configuración
 * - PATCH  /parkings/shift-rates/{configId}/toggle → Activar/Desactivar
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/parkings")
@RequiredArgsConstructor
public class ParkingShiftRateRestAdapter {

   private final ConfigureParkingShiftRatesUseCase configureParkingShiftRatesUseCase;
   private final ParkingShiftRateRestMapper mapper;

   // ========================= CONFIGURACIÓN DE TARIFAS POR TURNO =========================

   /**
    * Configura tarifas por turno para un parqueo.
    * Esta configuración se aplica a TODAS las zonas del parqueo.
    *
    * Paso 4 del STEPPER: Después de crear Parking → Zones → Spaces.
    *
    * @param parkingId ID del parqueo
    * @param request configuraciones de shift-rate a aplicar
    * @return lista de configuraciones guardadas
    */
   @PostMapping("/{parkingId}/shift-rates")
   public ResponseEntity<List<ParkingShiftRateResponse>> configureShiftRates(
         @PathVariable Long parkingId,
         @Valid @RequestBody ConfigureParkingShiftRatesRequest request
   ) {
      log.info("POST /parkings/{}/shift-rates - Configurando tarifas por turno", parkingId);

      ConfigureParkingShiftRatesCommand command = mapper.toCommand(parkingId, request);

      List<ParkingShiftRateDto> dtos = configureParkingShiftRatesUseCase.configureShiftRates(command);

      List<ParkingShiftRateResponse> responses = dtos.stream()
            .map(mapper::toResponse)
            .toList();

      log.info("{} configuraciones guardadas para parqueo {}", responses.size(), parkingId);

      return ResponseEntity.status(HttpStatus.CREATED).body(responses);
   }

   /**
    * Obtiene las configuraciones de tarifas por turno de un parqueo.
    *
    * @param parkingId ID del parqueo
    * @return lista de configuraciones activas e inactivas
    */
   @GetMapping("/{parkingId}/shift-rates")
   public ResponseEntity<List<ParkingShiftRateResponse>> getParkingShiftRates(
         @PathVariable Long parkingId
   ) {
      log.debug("GET /parkings/{}/shift-rates - Obteniendo configuraciones", parkingId);

      List<ParkingShiftRateDto> dtos = configureParkingShiftRatesUseCase.getParkingShiftRates(parkingId);

      List<ParkingShiftRateResponse> responses = dtos.stream()
            .map(mapper::toResponse)
            .toList();

      log.debug("{} configuraciones encontradas", responses.size());

      return ResponseEntity.ok(responses);
   }

   /**
    * Elimina una configuración específica de tarifa por turno.
    * Útil para remover combinaciones de shift-rate que ya no se usan.
    *
    * @param configId ID de la configuración a eliminar
    * @return 204 No Content
    */
   @DeleteMapping("/shift-rates/{configId}")
   public ResponseEntity<Void> deleteShiftRateConfig(@PathVariable Long configId) {
      log.info("DELETE /parkings/shift-rates/{} - Eliminando configuración", configId);

      configureParkingShiftRatesUseCase.deleteShiftRateConfig(configId);

      log.info("Configuración {} eliminada", configId);

      return ResponseEntity.noContent().build();
   }

   /**
    * Activa/desactiva una configuración de tarifa por turno.
    * Toggle entre status = true (activo) y status = false (inactivo).
    *
    * @param configId ID de la configuración
    * @return configuración actualizada con nuevo estado
    */
   @PatchMapping("/shift-rates/{configId}/toggle")
   public ResponseEntity<ParkingShiftRateResponse> toggleShiftRateStatus(
         @PathVariable Long configId
   ) {
      log.info("PATCH /parkings/shift-rates/{}/toggle - Cambiando estado", configId);

      ParkingShiftRateDto dto = configureParkingShiftRatesUseCase.toggleShiftRateStatus(configId);

      log.info("Estado cambiado a: {}", dto.status() ? "ACTIVO" : "INACTIVO");

      return ResponseEntity.ok(mapper.toResponse(dto));
   }
}