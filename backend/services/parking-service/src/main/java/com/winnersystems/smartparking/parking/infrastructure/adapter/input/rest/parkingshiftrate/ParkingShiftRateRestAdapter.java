package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate;

import com.winnersystems.smartparking.parking.application.port.input.parkingshiftrate.ConfigureParkingShiftRatesUseCase;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.request.ChangeShiftRateRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.request.ConfigureParkingShiftRatesRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.response.ParkingScheduleStatusResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.response.ParkingShiftRateResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.mapper.ParkingShiftRateRestMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Horarios y tarifas de cada parqueo (valen para todas sus zonas). Son parte de la
 * configuración del parqueo: leer exige parkings.read y cambiar parkings.update.
 *
 * Ruta pública en el gateway: /api/parking-service/v1/parkings/{parkingId}/shift-rates
 */
@RestController
@RequestMapping("/parkings")
public class ParkingShiftRateRestAdapter {

   private final ConfigureParkingShiftRatesUseCase useCase;
   private final ParkingShiftRateRestMapper mapper;

   public ParkingShiftRateRestAdapter(ConfigureParkingShiftRatesUseCase useCase, ParkingShiftRateRestMapper mapper) {
      this.useCase = useCase;
      this.mapper = mapper;
   }

   /** Reemplaza toda la configuración (paso 4 del asistente y edición del parqueo) */
   @PostMapping("/{parkingId}/shift-rates")
   @PreAuthorize("hasAuthority('parkings.update') or hasAuthority('parkings.create')")
   public ResponseEntity<List<ParkingShiftRateResponse>> configureShiftRates(
         @PathVariable Long parkingId, @Valid @RequestBody ConfigureParkingShiftRatesRequest request) {
      return ResponseEntity.ok(useCase.configureShiftRates(mapper.toCommand(parkingId, request)).stream()
            .map(mapper::toResponse)
            .toList());
   }

   @GetMapping("/{parkingId}/shift-rates")
   @PreAuthorize("hasAuthority('parkings.read')")
   public ResponseEntity<List<ParkingShiftRateResponse>> getParkingShiftRates(@PathVariable Long parkingId) {
      return ResponseEntity.ok(useCase.getParkingShiftRates(parkingId).stream()
            .map(mapper::toResponse)
            .toList());
   }

   /** ¿Atiende ahora? Lo usan la entrada de vehículos y el detalle del parqueo: solo exige sesión */
   @GetMapping("/{parkingId}/shift-rates/current")
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<ParkingScheduleStatusResponse> getCurrentSchedule(@PathVariable Long parkingId) {
      return ResponseEntity.ok(mapper.toResponse(useCase.getCurrentSchedule(parkingId)));
   }

   @PatchMapping("/shift-rates/{configId}/rate")
   @PreAuthorize("hasAuthority('parkings.update')")
   public ResponseEntity<ParkingShiftRateResponse> changeRate(@PathVariable Long configId,
                                                              @Valid @RequestBody ChangeShiftRateRequest request) {
      return ResponseEntity.ok(mapper.toResponse(useCase.changeRate(configId, request.rateId())));
   }

   @PatchMapping("/shift-rates/{configId}/toggle")
   @PreAuthorize("hasAuthority('parkings.update')")
   public ResponseEntity<ParkingShiftRateResponse> toggleShiftRateStatus(@PathVariable Long configId) {
      return ResponseEntity.ok(mapper.toResponse(useCase.toggleShiftRateStatus(configId)));
   }

   @DeleteMapping("/shift-rates/{configId}")
   @PreAuthorize("hasAuthority('parkings.update')")
   public ResponseEntity<Void> deleteShiftRateConfig(@PathVariable Long configId) {
      useCase.deleteShiftRateConfig(configId);
      return ResponseEntity.noContent().build();
   }
}
