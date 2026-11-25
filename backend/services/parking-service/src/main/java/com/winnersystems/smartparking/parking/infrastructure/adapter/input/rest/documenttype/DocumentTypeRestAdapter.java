package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype;

import com.winnersystems.smartparking.parking.application.dto.query.DocumentTypeDto;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.port.input.documenttype.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.dto.request.CreateDocumentTypeRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.dto.request.UpdateDocumentTypeRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.dto.response.DocumentTypeResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.mapper.DocumentTypeRestMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tipos de documento. Cada operación exige el permiso del rol activo (document-types.*).
 * /active solo exige sesión: lo usan los formularios de entrada y salida de vehículos.
 */
@RestController
@RequestMapping("/document-types")
public class DocumentTypeRestAdapter {

   private static final int MAX_PAGE_SIZE = 100;

   private final CreateDocumentTypeUseCase createUseCase;
   private final UpdateDocumentTypeUseCase updateUseCase;
   private final DeleteDocumentTypeUseCase deleteUseCase;
   private final GetDocumentTypeUseCase getUseCase;
   private final ListDocumentTypesUseCase listUseCase;
   private final ToggleDocumentTypeStatusUseCase toggleUseCase;
   private final RestoreDocumentTypeUseCase restoreUseCase;
   private final DocumentTypeRestMapper mapper;

   public DocumentTypeRestAdapter(
         CreateDocumentTypeUseCase createUseCase,
         UpdateDocumentTypeUseCase updateUseCase,
         DeleteDocumentTypeUseCase deleteUseCase,
         GetDocumentTypeUseCase getUseCase,
         ListDocumentTypesUseCase listUseCase,
         ToggleDocumentTypeStatusUseCase toggleUseCase,
         RestoreDocumentTypeUseCase restoreUseCase,
         DocumentTypeRestMapper mapper) {
      this.createUseCase = createUseCase;
      this.updateUseCase = updateUseCase;
      this.deleteUseCase = deleteUseCase;
      this.getUseCase = getUseCase;
      this.listUseCase = listUseCase;
      this.toggleUseCase = toggleUseCase;
      this.restoreUseCase = restoreUseCase;
      this.mapper = mapper;
   }

   /**
    * Lista paginada. search busca en el nombre y el código; status: true, false o vacío (todos).
    * deleted = true lista la papelera: exige además document-types.restore.
    */
   @GetMapping
   @PreAuthorize("hasAuthority('document-types.read') and (!#deleted or hasAuthority('document-types.restore'))")
   public ResponseEntity<PagedResponse<DocumentTypeResponse>> list(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "10") int size,
         @RequestParam(required = false) String search,
         @RequestParam(required = false) Boolean status,
         @RequestParam(defaultValue = "false") boolean deleted) {
      int safePage = Math.max(page, 0);
      int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
      PagedResponse<DocumentTypeDto> result = listUseCase.listAllDocumentTypes(safePage, safeSize, search, status, deleted);
      return ResponseEntity.ok(result.map(mapper::toResponse));
   }

   @GetMapping("/active")
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<List<DocumentTypeResponse>> listActive() {
      return ResponseEntity.ok(listUseCase.listAllActiveDocumentTypes().stream()
            .map(mapper::toResponse)
            .toList());
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('document-types.read')")
   public ResponseEntity<DocumentTypeResponse> getById(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(getUseCase.getDocumentTypeById(id)));
   }

   @PostMapping
   @PreAuthorize("hasAuthority('document-types.create')")
   public ResponseEntity<DocumentTypeResponse> create(@Valid @RequestBody CreateDocumentTypeRequest request) {
      DocumentTypeDto dto = createUseCase.createDocumentType(mapper.toCommand(request));
      return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(dto));
   }

   @PutMapping("/{id}")
   @PreAuthorize("hasAuthority('document-types.update')")
   public ResponseEntity<DocumentTypeResponse> update(
         @PathVariable Long id,
         @Valid @RequestBody UpdateDocumentTypeRequest request) {
      return ResponseEntity.ok(mapper.toResponse(updateUseCase.updateDocumentType(id, mapper.toCommand(request))));
   }

   /** Eliminación lógica; 409 si lo usan clientes o transacciones */
   @DeleteMapping("/{id}")
   @PreAuthorize("hasAuthority('document-types.delete')")
   public ResponseEntity<Void> delete(@PathVariable Long id) {
      deleteUseCase.deleteDocumentType(id);
      return ResponseEntity.noContent().build();
   }

   @PostMapping("/{id}/restore")
   @PreAuthorize("hasAuthority('document-types.restore')")
   public ResponseEntity<DocumentTypeResponse> restore(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(restoreUseCase.restoreDocumentType(id)));
   }

   @PatchMapping("/{id}/toggle-status")
   @PreAuthorize("hasAuthority('document-types.update')")
   public ResponseEntity<DocumentTypeResponse> toggleStatus(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleUseCase.toggleDocumentTypeStatus(id)));
   }

   @PatchMapping("/{id}/activate")
   @PreAuthorize("hasAuthority('document-types.update')")
   public ResponseEntity<DocumentTypeResponse> activate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleUseCase.activateDocumentTypeDto(id)));
   }

   @PatchMapping("/{id}/deactivate")
   @PreAuthorize("hasAuthority('document-types.update')")
   public ResponseEntity<DocumentTypeResponse> deactivate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleUseCase.deactivateDocumentTypeDto(id)));
   }
}
