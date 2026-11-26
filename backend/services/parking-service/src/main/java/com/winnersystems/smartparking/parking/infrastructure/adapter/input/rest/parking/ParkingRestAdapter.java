package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.port.input.parking.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.dto.request.CreateParkingRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.dto.request.UpdateParkingRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.dto.response.ParkingResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.mapper.ParkingRestMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Parqueos: CRUD, estado y papelera. Cada endpoint exige su permiso parkings.*.
 *
 * Ruta pública en el gateway: /api/parking-service/v1/parkings
 */
@RestController
@RequestMapping("/parkings")
public class ParkingRestAdapter {

   /** Tamaño máximo de página */
   private static final int MAX_PAGE_SIZE = 100;

   private final CreateParkingUseCase createParkingUseCase;
   private final UpdateParkingUseCase updateParkingUseCase;
   private final DeleteParkingUseCase deleteParkingUseCase;
   private final RestoreParkingUseCase restoreParkingUseCase;
   private final GetParkingUseCase getParkingUseCase;
   private final ListParkingsUseCase listParkingsUseCase;
   private final ToggleParkingStatusUseCase toggleParkingStatusUseCase;
   private final ParkingRestMapper mapper;

   public ParkingRestAdapter(CreateParkingUseCase createParkingUseCase,
                             UpdateParkingUseCase updateParkingUseCase,
                             DeleteParkingUseCase deleteParkingUseCase,
                             RestoreParkingUseCase restoreParkingUseCase,
                             GetParkingUseCase getParkingUseCase,
                             ListParkingsUseCase listParkingsUseCase,
                             ToggleParkingStatusUseCase toggleParkingStatusUseCase,
                             ParkingRestMapper mapper) {
      this.createParkingUseCase = createParkingUseCase;
      this.updateParkingUseCase = updateParkingUseCase;
      this.deleteParkingUseCase = deleteParkingUseCase;
      this.restoreParkingUseCase = restoreParkingUseCase;
      this.getParkingUseCase = getParkingUseCase;
      this.listParkingsUseCase = listParkingsUseCase;
      this.toggleParkingStatusUseCase = toggleParkingStatusUseCase;
      this.mapper = mapper;
   }

   // ========================= CONSULTAS =========================

   /**
    * Lista paginada. deleted = true lista la papelera y exige además parkings.restore.
    */
   @GetMapping
   @PreAuthorize("hasAuthority('parkings.read') and (!#deleted or hasAuthority('parkings.restore'))")
   public ResponseEntity<PagedResponse<ParkingResponse>> listAllParkings(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size,
         @RequestParam(required = false) String search,
         @RequestParam(required = false) String status,
         @RequestParam(defaultValue = "false") boolean deleted) {
      int safePage = Math.max(page, 0);
      int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
      return ResponseEntity.ok(listParkingsUseCase
            .listAllParkings(safePage, safeSize, search, status, deleted)
            .map(mapper::toResponse));
   }

   /** Parqueos activos para los selectores (entrada de vehículos, zonas, asignaciones): solo exige sesión */
   @GetMapping("/active")
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<List<ParkingResponse>> listActiveParkings() {
      return ResponseEntity.ok(listParkingsUseCase.listAllActiveParkings().stream()
            .map(mapper::toResponse)
            .toList());
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('parkings.read')")
   public ResponseEntity<ParkingResponse> getParkingById(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(getParkingUseCase.getParkingById(id)));
   }

   // ========================= CREAR, EDITAR, ELIMINAR =========================

   @PostMapping
   @PreAuthorize("hasAuthority('parkings.create')")
   public ResponseEntity<ParkingResponse> createParking(@Valid @RequestBody CreateParkingRequest request) {
      return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toResponse(createParkingUseCase.createParking(mapper.toCommand(request))));
   }

   @PutMapping("/{id}")
   @PreAuthorize("hasAuthority('parkings.update')")
   public ResponseEntity<ParkingResponse> updateParking(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateParkingRequest request) {
      return ResponseEntity.ok(mapper.toResponse(updateParkingUseCase.updateParking(id, mapper.toCommand(request))));
   }

   /** 409 si hay vehículos dentro */
   @DeleteMapping("/{id}")
   @PreAuthorize("hasAuthority('parkings.delete')")
   public ResponseEntity<Void> deleteParking(@PathVariable Long id) {
      deleteParkingUseCase.deleteParking(id);
      return ResponseEntity.noContent().build();
   }

   /** Deshace una eliminación: el parqueo vuelve activo con sus zonas y espacios */
   @PostMapping("/{id}/restore")
   @PreAuthorize("hasAuthority('parkings.restore')")
   public ResponseEntity<ParkingResponse> restoreParking(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(restoreParkingUseCase.restoreParking(id)));
   }

   // ========================= ESTADO =========================

   @PatchMapping("/{id}/toggle-status")
   @PreAuthorize("hasAuthority('parkings.update')")
   public ResponseEntity<ParkingResponse> toggleStatus(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleParkingStatusUseCase.toggleParkingStatus(id)));
   }

   @PatchMapping("/{id}/activate")
   @PreAuthorize("hasAuthority('parkings.update')")
   public ResponseEntity<ParkingResponse> activate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleParkingStatusUseCase.activateParking(id)));
   }

   @PatchMapping("/{id}/deactivate")
   @PreAuthorize("hasAuthority('parkings.update')")
   public ResponseEntity<ParkingResponse> deactivate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleParkingStatusUseCase.deactivateParking(id)));
   }

   @PatchMapping("/{id}/maintenance")
   @PreAuthorize("hasAuthority('parkings.update')")
   public ResponseEntity<ParkingResponse> setInMaintenance(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleParkingStatusUseCase.setInMaintenance(id)));
   }

   @PatchMapping("/{id}/out-of-service")
   @PreAuthorize("hasAuthority('parkings.update')")
   public ResponseEntity<ParkingResponse> setOutOfService(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleParkingStatusUseCase.setOutOfService(id)));
   }
}
