package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.port.input.space.*;
import com.winnersystems.smartparking.parking.application.service.space.SpaceService;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.dto.request.CreateSpaceRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.dto.request.UpdateSpaceRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.dto.response.SpaceResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.mapper.SpaceRestMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Espacios de las zonas: CRUD, creación por lote, estado y papelera.
 * Cada endpoint exige su permiso spaces.*.
 *
 * Ruta pública en el gateway: /api/parking-service/v1/spaces
 */
@RestController
@RequestMapping("/spaces")
public class SpaceRestAdapter {

   /** Tamaño máximo de página */
   private static final int MAX_PAGE_SIZE = 100;

   private final CreateSpaceUseCase createSpaceUseCase;
   private final UpdateSpaceUseCase updateSpaceUseCase;
   private final DeleteSpaceUseCase deleteSpaceUseCase;
   private final RestoreSpaceUseCase restoreSpaceUseCase;
   private final GetSpaceUseCase getSpaceUseCase;
   private final ListSpacesUseCase listSpacesUseCase;
   private final ToggleSpaceStatusUseCase toggleSpaceStatusUseCase;
   private final SpaceRestMapper mapper;

   public SpaceRestAdapter(CreateSpaceUseCase createSpaceUseCase,
                           UpdateSpaceUseCase updateSpaceUseCase,
                           DeleteSpaceUseCase deleteSpaceUseCase,
                           RestoreSpaceUseCase restoreSpaceUseCase,
                           GetSpaceUseCase getSpaceUseCase,
                           ListSpacesUseCase listSpacesUseCase,
                           ToggleSpaceStatusUseCase toggleSpaceStatusUseCase,
                           SpaceRestMapper mapper) {
      this.createSpaceUseCase = createSpaceUseCase;
      this.updateSpaceUseCase = updateSpaceUseCase;
      this.deleteSpaceUseCase = deleteSpaceUseCase;
      this.restoreSpaceUseCase = restoreSpaceUseCase;
      this.getSpaceUseCase = getSpaceUseCase;
      this.listSpacesUseCase = listSpacesUseCase;
      this.toggleSpaceStatusUseCase = toggleSpaceStatusUseCase;
      this.mapper = mapper;
   }

   // ========================= CONSULTAS =========================

   /**
    * Lista paginada, filtrable por zona, parqueo, estado y tipo.
    * deleted = true lista la papelera y exige además spaces.restore.
    */
   @GetMapping
   @PreAuthorize("hasAuthority('spaces.read') and (!#deleted or hasAuthority('spaces.restore'))")
   public ResponseEntity<PagedResponse<SpaceResponse>> listAllSpaces(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size,
         @RequestParam(required = false) String search,
         @RequestParam(required = false) String status,
         @RequestParam(required = false) String type,
         @RequestParam(required = false) Long zoneId,
         @RequestParam(required = false) Long parkingId,
         @RequestParam(defaultValue = "false") boolean deleted) {
      int safePage = Math.max(page, 0);
      int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
      return ResponseEntity.ok(listSpacesUseCase
            .listAllSpaces(safePage, safeSize, search, status, type, zoneId, parkingId, deleted)
            .map(mapper::toResponse));
   }

   /** Todos los espacios vigentes de una zona (detalle de la zona, transacciones activas) */
   @GetMapping("/zone/{zoneId}")
   @PreAuthorize("hasAnyAuthority('spaces.read', 'zones.read')")
   public ResponseEntity<List<SpaceResponse>> listByZone(@PathVariable Long zoneId) {
      return ResponseEntity.ok(listSpacesUseCase.listSpacesByZone(zoneId).stream()
            .map(mapper::toResponse)
            .toList());
   }

   /** Espacios libres para registrar una entrada: solo exige sesión */
   @GetMapping("/zone/{zoneId}/available")
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<List<SpaceResponse>> listAvailableByZone(@PathVariable Long zoneId) {
      return ResponseEntity.ok(listSpacesUseCase.listAvailableByZone(zoneId).stream()
            .map(mapper::toResponse)
            .toList());
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('spaces.read')")
   public ResponseEntity<SpaceResponse> getSpaceById(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(getSpaceUseCase.getSpaceById(id)));
   }

   // ========================= CREAR, EDITAR, ELIMINAR =========================

   @PostMapping
   @PreAuthorize("hasAuthority('spaces.create')")
   public ResponseEntity<SpaceResponse> createSpace(@Valid @RequestBody CreateSpaceRequest request) {
      return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toResponse(createSpaceUseCase.createSpace(mapper.toCommand(request))));
   }

   /** Creación por lote (paso 3 del asistente): todo o nada, máximo SpaceService.MAX_BATCH */
   @PostMapping("/batch")
   @PreAuthorize("hasAuthority('spaces.bulk-create')")
   public ResponseEntity<List<SpaceResponse>> createBatch(
         @RequestBody List<@Valid CreateSpaceRequest> requests) {
      return ResponseEntity.status(HttpStatus.CREATED).body(createSpaceUseCase
            .createSpaces(requests.stream().map(mapper::toCommand).toList()).stream()
            .map(mapper::toResponse)
            .toList());
   }

   @PutMapping("/{id}")
   @PreAuthorize("hasAuthority('spaces.update')")
   public ResponseEntity<SpaceResponse> updateSpace(@PathVariable Long id, @Valid @RequestBody UpdateSpaceRequest request) {
      return ResponseEntity.ok(mapper.toResponse(updateSpaceUseCase.updateSpace(id, mapper.toCommand(request))));
   }

   /** 409 si hay un vehículo dentro */
   @DeleteMapping("/{id}")
   @PreAuthorize("hasAuthority('spaces.delete')")
   public ResponseEntity<Void> deleteSpace(@PathVariable Long id) {
      deleteSpaceUseCase.deleteSpace(id);
      return ResponseEntity.noContent().build();
   }

   /** Deshace una eliminación: vuelve libre (409 si su zona sigue eliminada) */
   @PostMapping("/{id}/restore")
   @PreAuthorize("hasAuthority('spaces.restore')")
   public ResponseEntity<SpaceResponse> restoreSpace(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(restoreSpaceUseCase.restoreSpace(id)));
   }

   // ========================= ESTADO =========================

   @PatchMapping("/{id}/toggle-status")
   @PreAuthorize("hasAuthority('spaces.update')")
   public ResponseEntity<SpaceResponse> toggleStatus(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleSpaceStatusUseCase.toggleSpaceStatus(id)));
   }

   /** Ocupación detectada sin transacción (cámara, sensor u operador) */
   @PatchMapping("/{id}/occupy")
   @PreAuthorize("hasAuthority('spaces.update')")
   public ResponseEntity<SpaceResponse> occupy(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleSpaceStatusUseCase.markAsOccupied(id)));
   }

   /** Libera o habilita el espacio (409 si hay un vehículo registrado dentro) */
   @PatchMapping("/{id}/release")
   @PreAuthorize("hasAuthority('spaces.update')")
   public ResponseEntity<SpaceResponse> release(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleSpaceStatusUseCase.markAsAvailable(id)));
   }

   @PatchMapping("/{id}/maintenance")
   @PreAuthorize("hasAuthority('spaces.update')")
   public ResponseEntity<SpaceResponse> setInMaintenance(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleSpaceStatusUseCase.setInMaintenance(id)));
   }

   @PatchMapping("/{id}/out-of-service")
   @PreAuthorize("hasAuthority('spaces.update')")
   public ResponseEntity<SpaceResponse> setOutOfService(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleSpaceStatusUseCase.setOutOfService(id)));
   }
}
