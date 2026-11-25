package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.RateSimulationDto;
import com.winnersystems.smartparking.parking.application.port.input.rate.*;
import com.winnersystems.smartparking.parking.domain.model.Rate.BillingRules;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.dto.request.CreateRateRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.dto.request.RatePreviewRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.dto.request.UpdateRateRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.dto.response.RateResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.mapper.RateRestMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de tarifas. Por el gateway: /api/parking-service/v1/rates.
 *
 * Cada endpoint exige un permiso del rol activo (rates.read, rates.create...). La lista de
 * tarifas activas solo exige sesión: la usan los formularios de parqueos.
 */
@RestController
@RequestMapping("/rates")
@RequiredArgsConstructor
public class RateRestAdapter {

   /** Tamaño máximo de página */
   private static final int MAX_PAGE_SIZE = 100;

   private final CreateRateUseCase createRateUseCase;
   private final UpdateRateUseCase updateRateUseCase;
   private final DeleteRateUseCase deleteRateUseCase;
   private final GetRateUseCase getRateUseCase;
   private final ListRatesUseCase listRatesUseCase;
   private final ToggleRateStatusUseCase toggleRateStatusUseCase;
   private final RestoreRateUseCase restoreRateUseCase;
   private final SimulateRateUseCase simulateRateUseCase;
   private final RateRestMapper mapper;

   /**
    * Lista paginada. deleted = true lista la papelera y exige además rates.restore.
    */
   @GetMapping
   @PreAuthorize("hasAuthority('rates.read') and (!#deleted or hasAuthority('rates.restore'))")
   public ResponseEntity<PagedResponse<RateResponse>> listAllRates(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size,
         @RequestParam(required = false) String search,
         @RequestParam(required = false) Boolean status,
         @RequestParam(defaultValue = "false") boolean deleted) {
      int safePage = Math.max(page, 0);
      int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
      return ResponseEntity.ok(
            listRatesUseCase.listAllRates(safePage, safeSize, search, status, deleted).map(mapper::toResponse));
   }

   @GetMapping("/active")
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<List<RateResponse>> listActiveRates() {
      return ResponseEntity.ok(listRatesUseCase.listAllActiveRates().stream()
            .map(mapper::toResponse)
            .toList());
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('rates.read')")
   public ResponseEntity<RateResponse> getRateById(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(getRateUseCase.getRateById(id)));
   }

   /**
    * Simula cuánto se cobraría con los datos del formulario (no guarda nada).
    * Usa el mismo cálculo que la salida de un vehículo.
    */
   @PostMapping("/simulate")
   @PreAuthorize("hasAnyAuthority('rates.read', 'rates.create', 'rates.update')")
   public ResponseEntity<RateSimulationDto> simulate(@Valid @RequestBody RatePreviewRequest request) {
      BillingRules rules = new BillingRules(request.getBillingUnitMinutes(), request.getMinimumMinutes(),
            request.getGraceMinutes(), request.getToleranceMinutes());
      return ResponseEntity.ok(simulateRateUseCase.simulate(request.getAmount(), rules, request.getDurations()));
   }

   @PostMapping
   @PreAuthorize("hasAuthority('rates.create')")
   public ResponseEntity<RateResponse> createRate(@Valid @RequestBody CreateRateRequest request) {
      return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toResponse(createRateUseCase.createRate(mapper.toCommand(request))));
   }

   @PutMapping("/{id}")
   @PreAuthorize("hasAuthority('rates.update')")
   public ResponseEntity<RateResponse> updateRate(@PathVariable Long id, @Valid @RequestBody UpdateRateRequest request) {
      return ResponseEntity.ok(mapper.toResponse(updateRateUseCase.updateRate(id, mapper.toCommand(request))));
   }

   /** 409 si algún parqueo usa la tarifa */
   @DeleteMapping("/{id}")
   @PreAuthorize("hasAuthority('rates.delete')")
   public ResponseEntity<Void> deleteRate(@PathVariable Long id) {
      deleteRateUseCase.deleteRate(id);
      return ResponseEntity.noContent().build();
   }

   /** Deshace una eliminación: la tarifa vuelve activa */
   @PostMapping("/{id}/restore")
   @PreAuthorize("hasAuthority('rates.restore')")
   public ResponseEntity<RateResponse> restoreRate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(restoreRateUseCase.restoreRate(id)));
   }

   @PatchMapping("/{id}/toggle-status")
   @PreAuthorize("hasAuthority('rates.update')")
   public ResponseEntity<RateResponse> toggleStatus(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleRateStatusUseCase.toggleRateStatus(id)));
   }

   @PatchMapping("/{id}/activate")
   @PreAuthorize("hasAuthority('rates.update')")
   public ResponseEntity<RateResponse> activateRate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleRateStatusUseCase.activateRate(id)));
   }

   @PatchMapping("/{id}/deactivate")
   @PreAuthorize("hasAuthority('rates.update')")
   public ResponseEntity<RateResponse> deactivateRate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleRateStatusUseCase.deactivateRate(id)));
   }
}
