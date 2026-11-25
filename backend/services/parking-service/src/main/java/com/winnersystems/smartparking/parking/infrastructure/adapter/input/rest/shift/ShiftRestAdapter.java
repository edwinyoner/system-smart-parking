package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.port.input.shift.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift.dto.request.CreateShiftRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift.dto.request.UpdateShiftRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift.dto.response.ShiftResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift.mapper.ShiftRestMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de turnos horarios. Por el gateway: /api/parking-service/v1/shifts.
 *
 * Cada endpoint exige un permiso del rol activo (shifts.read, shifts.create...), que
 * llega en el JWT. La lista de turnos activos solo exige sesión: la usan los formularios
 * de parqueos para elegir horarios, aunque el usuario no administre turnos.
 */
@RestController
@RequestMapping("/shifts")
@RequiredArgsConstructor
public class ShiftRestAdapter {

   /** Tamaño máximo de página, para que nadie pida la tabla completa de una vez */
   private static final int MAX_PAGE_SIZE = 100;

   private final CreateShiftUseCase createShiftUseCase;
   private final UpdateShiftUseCase updateShiftUseCase;
   private final DeleteShiftUseCase deleteShiftUseCase;
   private final GetShiftUseCase getShiftUseCase;
   private final ListShiftsUseCase listShiftsUseCase;
   private final ToggleShiftStatusUseCase toggleShiftStatusUseCase;
   private final ShiftRestMapper mapper;

   /**
    * Lista paginada. search busca en el nombre y el código; status: true, false o vacío (todos).
    */
   @GetMapping
   @PreAuthorize("hasAuthority('shifts.read')")
   public ResponseEntity<PagedResponse<ShiftResponse>> listAllShifts(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size,
         @RequestParam(required = false) String search,
         @RequestParam(required = false) Boolean status) {
      int safePage = Math.max(page, 0);
      int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
      return ResponseEntity.ok(
            listShiftsUseCase.listAllShifts(safePage, safeSize, search, status).map(mapper::toResponse));
   }

   @GetMapping("/active")
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<List<ShiftResponse>> listActiveShifts() {
      return ResponseEntity.ok(listShiftsUseCase.listAllActiveShifts().stream()
            .map(mapper::toResponse)
            .toList());
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('shifts.read')")
   public ResponseEntity<ShiftResponse> getShiftById(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(getShiftUseCase.getShiftById(id)));
   }

   @PostMapping
   @PreAuthorize("hasAuthority('shifts.create')")
   public ResponseEntity<ShiftResponse> createShift(@Valid @RequestBody CreateShiftRequest request) {
      return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toResponse(createShiftUseCase.createShift(mapper.toCommand(request))));
   }

   @PutMapping("/{id}")
   @PreAuthorize("hasAuthority('shifts.update')")
   public ResponseEntity<ShiftResponse> updateShift(
         @PathVariable Long id,
         @Valid @RequestBody UpdateShiftRequest request) {
      return ResponseEntity.ok(mapper.toResponse(updateShiftUseCase.updateShift(id, mapper.toCommand(request))));
   }

   /** 409 si algún parqueo usa el turno */
   @DeleteMapping("/{id}")
   @PreAuthorize("hasAuthority('shifts.delete')")
   public ResponseEntity<Void> deleteShift(@PathVariable Long id) {
      deleteShiftUseCase.deleteShift(id);
      return ResponseEntity.noContent().build();
   }

   @PatchMapping("/{id}/toggle-status")
   @PreAuthorize("hasAuthority('shifts.update')")
   public ResponseEntity<ShiftResponse> toggleStatus(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleShiftStatusUseCase.toggleShiftStatus(id)));
   }

   @PatchMapping("/{id}/activate")
   @PreAuthorize("hasAuthority('shifts.update')")
   public ResponseEntity<ShiftResponse> activateShift(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleShiftStatusUseCase.activateShift(id)));
   }

   @PatchMapping("/{id}/deactivate")
   @PreAuthorize("hasAuthority('shifts.update')")
   public ResponseEntity<ShiftResponse> deactivateShift(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleShiftStatusUseCase.deactivateShift(id)));
   }
}
