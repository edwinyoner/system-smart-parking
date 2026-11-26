package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment;

import com.winnersystems.smartparking.parking.application.port.input.operatorassignment.ManageOperatorAssignmentsUseCase;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.dto.request.AssignOperatorRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.dto.request.UpdateOperatorAssignmentRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.dto.response.OperatorAssignmentResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.mapper.OperatorAssignmentRestMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Asignación de operadores a parqueos y zonas por turno. Se protege por permiso
 * (operator-assignments.*), no por nombre de rol: los roles se crean desde la interfaz.
 *
 * Ruta pública en el gateway: /api/parking-service/v1/operator-assignments
 */
@RestController
@RequestMapping("/operator-assignments")
public class OperatorAssignmentRestAdapter {

   private final ManageOperatorAssignmentsUseCase useCase;
   private final OperatorAssignmentRestMapper mapper;

   public OperatorAssignmentRestAdapter(ManageOperatorAssignmentsUseCase useCase, OperatorAssignmentRestMapper mapper) {
      this.useCase = useCase;
      this.mapper = mapper;
   }

   @PostMapping
   @PreAuthorize("hasAuthority('operator-assignments.create')")
   public ResponseEntity<OperatorAssignmentResponse> assign(@Valid @RequestBody AssignOperatorRequest request) {
      return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(useCase.assign(mapper.toCommand(request))));
   }

   /** Filtra por parqueo (y zona) o por operador; al menos uno de los dos */
   @GetMapping
   @PreAuthorize("hasAuthority('operator-assignments.read')")
   public ResponseEntity<List<OperatorAssignmentResponse>> list(
         @RequestParam(required = false) Long parkingId,
         @RequestParam(required = false) Long zoneId,
         @RequestParam(required = false) Long operatorId,
         @RequestParam(required = false) String status) {
      return ResponseEntity.ok(useCase.list(parkingId, zoneId, operatorId, status).stream()
            .map(mapper::toResponse).toList());
   }

   /** Asignaciones de quien consulta: solo exige sesión */
   @GetMapping("/me")
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<List<OperatorAssignmentResponse>> listMine(@RequestParam(required = false) String status) {
      return ResponseEntity.ok(useCase.listMine(status).stream().map(mapper::toResponse).toList());
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('operator-assignments.read')")
   public ResponseEntity<OperatorAssignmentResponse> get(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(useCase.get(id)));
   }

   @PatchMapping("/{id}")
   @PreAuthorize("hasAuthority('operator-assignments.update')")
   public ResponseEntity<OperatorAssignmentResponse> update(@PathVariable Long id,
                                                            @Valid @RequestBody UpdateOperatorAssignmentRequest request) {
      return ResponseEntity.ok(mapper.toResponse(useCase.update(id, mapper.toCommand(request))));
   }

   /**
    * Termina la asignación desde hoy. Si aún no había empezado se cancela y responde 204.
    */
   @PostMapping("/{id}/finish")
   @PreAuthorize("hasAuthority('operator-assignments.delete')")
   public ResponseEntity<OperatorAssignmentResponse> finish(@PathVariable Long id) {
      return useCase.finish(id)
            .map(dto -> ResponseEntity.ok(mapper.toResponse(dto)))
            .orElseGet(() -> ResponseEntity.noContent().build());
   }
}
