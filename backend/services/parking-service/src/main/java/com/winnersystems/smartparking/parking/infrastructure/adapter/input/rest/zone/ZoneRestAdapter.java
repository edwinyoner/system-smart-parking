package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.port.input.zone.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.dto.request.CreateZoneRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.dto.request.UpdateZoneRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.dto.response.ZoneResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.mapper.ZoneRestMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Zonas de los parqueos: CRUD, estado y papelera. Cada endpoint exige su permiso zones.*.
 *
 * Ruta pública en el gateway: /api/parking-service/v1/zones
 */
@RestController
@RequestMapping("/zones")
public class ZoneRestAdapter {

   /** Tamaño máximo de página */
   private static final int MAX_PAGE_SIZE = 100;

   private final CreateZoneUseCase createZoneUseCase;
   private final UpdateZoneUseCase updateZoneUseCase;
   private final DeleteZoneUseCase deleteZoneUseCase;
   private final RestoreZoneUseCase restoreZoneUseCase;
   private final GetZoneUseCase getZoneUseCase;
   private final ListZonesUseCase listZonesUseCase;
   private final ToggleZoneStatusUseCase toggleZoneStatusUseCase;
   private final ZoneRestMapper mapper;

   public ZoneRestAdapter(CreateZoneUseCase createZoneUseCase,
                          UpdateZoneUseCase updateZoneUseCase,
                          DeleteZoneUseCase deleteZoneUseCase,
                          RestoreZoneUseCase restoreZoneUseCase,
                          GetZoneUseCase getZoneUseCase,
                          ListZonesUseCase listZonesUseCase,
                          ToggleZoneStatusUseCase toggleZoneStatusUseCase,
                          ZoneRestMapper mapper) {
      this.createZoneUseCase = createZoneUseCase;
      this.updateZoneUseCase = updateZoneUseCase;
      this.deleteZoneUseCase = deleteZoneUseCase;
      this.restoreZoneUseCase = restoreZoneUseCase;
      this.getZoneUseCase = getZoneUseCase;
      this.listZonesUseCase = listZonesUseCase;
      this.toggleZoneStatusUseCase = toggleZoneStatusUseCase;
      this.mapper = mapper;
   }

   // ========================= CONSULTAS =========================

   /**
    * Lista paginada, filtrable por parqueo. deleted = true lista la papelera y exige además zones.restore.
    */
   @GetMapping
   @PreAuthorize("hasAuthority('zones.read') and (!#deleted or hasAuthority('zones.restore'))")
   public ResponseEntity<PagedResponse<ZoneResponse>> listAllZones(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size,
         @RequestParam(required = false) String search,
         @RequestParam(required = false) String status,
         @RequestParam(required = false) Long parkingId,
         @RequestParam(defaultValue = "false") boolean deleted) {
      int safePage = Math.max(page, 0);
      int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
      return ResponseEntity.ok(listZonesUseCase
            .listAllZones(safePage, safeSize, search, status, parkingId, deleted)
            .map(mapper::toResponse));
   }

   /** Zonas que reciben vehículos, para los selectores (entrada, asignaciones): solo exige sesión */
   @GetMapping("/active")
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<List<ZoneResponse>> listActiveZones(@RequestParam(required = false) Long parkingId) {
      return ResponseEntity.ok(listZonesUseCase.listAllActiveZones(parkingId).stream()
            .map(mapper::toResponse)
            .toList());
   }

   /** Todas las zonas vigentes de un parqueo, en cualquier estado (detalle del parqueo y asistente) */
   @GetMapping("/parking/{parkingId}")
   @PreAuthorize("hasAnyAuthority('zones.read', 'parkings.read')")
   public ResponseEntity<List<ZoneResponse>> listByParking(@PathVariable Long parkingId) {
      return ResponseEntity.ok(listZonesUseCase.listZonesByParking(parkingId).stream()
            .map(mapper::toResponse)
            .toList());
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('zones.read')")
   public ResponseEntity<ZoneResponse> getZoneById(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(getZoneUseCase.getZoneById(id)));
   }

   // ========================= CREAR, EDITAR, ELIMINAR =========================

   @PostMapping
   @PreAuthorize("hasAuthority('zones.create')")
   public ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody CreateZoneRequest request) {
      return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toResponse(createZoneUseCase.createZone(mapper.toCommand(request))));
   }

   @PutMapping("/{id}")
   @PreAuthorize("hasAuthority('zones.update')")
   public ResponseEntity<ZoneResponse> updateZone(@PathVariable Long id, @Valid @RequestBody UpdateZoneRequest request) {
      return ResponseEntity.ok(mapper.toResponse(updateZoneUseCase.updateZone(id, mapper.toCommand(request))));
   }

   /** 409 si hay vehículos dentro */
   @DeleteMapping("/{id}")
   @PreAuthorize("hasAuthority('zones.delete')")
   public ResponseEntity<Void> deleteZone(@PathVariable Long id) {
      deleteZoneUseCase.deleteZone(id);
      return ResponseEntity.noContent().build();
   }

   /** Deshace una eliminación (409 si su parqueo sigue eliminado) */
   @PostMapping("/{id}/restore")
   @PreAuthorize("hasAuthority('zones.restore')")
   public ResponseEntity<ZoneResponse> restoreZone(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(restoreZoneUseCase.restoreZone(id)));
   }

   // ========================= ESTADO =========================

   @PatchMapping("/{id}/toggle-status")
   @PreAuthorize("hasAuthority('zones.update')")
   public ResponseEntity<ZoneResponse> toggleStatus(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleZoneStatusUseCase.toggleZoneStatus(id)));
   }

   @PatchMapping("/{id}/activate")
   @PreAuthorize("hasAuthority('zones.update')")
   public ResponseEntity<ZoneResponse> activate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleZoneStatusUseCase.activateZone(id)));
   }

   @PatchMapping("/{id}/deactivate")
   @PreAuthorize("hasAuthority('zones.update')")
   public ResponseEntity<ZoneResponse> deactivate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleZoneStatusUseCase.deactivateZone(id)));
   }

   @PatchMapping("/{id}/maintenance")
   @PreAuthorize("hasAuthority('zones.update')")
   public ResponseEntity<ZoneResponse> setInMaintenance(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleZoneStatusUseCase.setInMaintenance(id)));
   }

   @PatchMapping("/{id}/out-of-service")
   @PreAuthorize("hasAuthority('zones.update')")
   public ResponseEntity<ZoneResponse> setOutOfService(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleZoneStatusUseCase.setOutOfService(id)));
   }
}
