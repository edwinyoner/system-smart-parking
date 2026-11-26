package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.payment.application.port.input.paymenttype.*;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.dto.request.CreatePaymentTypeRequest;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.dto.request.UpdatePaymentTypeRequest;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.dto.response.PaymentTypeResponse;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.mapper.PaymentTypeRestMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tipos de pago. Cada operación exige el permiso del rol activo (payment-types.*).
 * /active solo exige sesión: lo usa el cobro en la salida de vehículos.
 */
@RestController
@RequestMapping("/payment-types")
public class PaymentTypeRestAdapter {

   private static final int MAX_PAGE_SIZE = 100;

   private final CreatePaymentTypeUseCase createUseCase;
   private final UpdatePaymentTypeUseCase updateUseCase;
   private final DeletePaymentTypeUseCase deleteUseCase;
   private final GetPaymentTypeUseCase getUseCase;
   private final ListPaymentTypesUseCase listUseCase;
   private final TogglePaymentTypeStatusUseCase toggleUseCase;
   private final RestorePaymentTypeUseCase restoreUseCase;
   private final PaymentTypeRestMapper mapper;

   public PaymentTypeRestAdapter(
         CreatePaymentTypeUseCase createUseCase,
         UpdatePaymentTypeUseCase updateUseCase,
         DeletePaymentTypeUseCase deleteUseCase,
         GetPaymentTypeUseCase getUseCase,
         ListPaymentTypesUseCase listUseCase,
         TogglePaymentTypeStatusUseCase toggleUseCase,
         RestorePaymentTypeUseCase restoreUseCase,
         PaymentTypeRestMapper mapper) {
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
    * deleted = true lista la papelera: exige además payment-types.restore.
    */
   @GetMapping
   @PreAuthorize("hasAuthority('payment-types.read') and (!#deleted or hasAuthority('payment-types.restore'))")
   public ResponseEntity<PagedResponse<PaymentTypeResponse>> list(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "10") int size,
         @RequestParam(required = false) String search,
         @RequestParam(required = false) Boolean status,
         @RequestParam(defaultValue = "false") boolean deleted) {
      int safePage = Math.max(page, 0);
      int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
      return ResponseEntity.ok(
            listUseCase.listAllPaymentTypes(safePage, safeSize, search, status, deleted).map(mapper::toResponse));
   }

   /** Activos; con channel (por ejemplo OPERATOR), solo los que se ofrecen en ese canal */
   @GetMapping("/active")
   @PreAuthorize("isAuthenticated()")
   public ResponseEntity<List<PaymentTypeResponse>> listActive(@RequestParam(required = false) PaymentChannel channel) {
      return ResponseEntity.ok(listUseCase.listActivePaymentTypes(channel).stream()
            .map(mapper::toResponse)
            .toList());
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('payment-types.read')")
   public ResponseEntity<PaymentTypeResponse> getById(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(getUseCase.getPaymentTypeById(id)));
   }

   @PostMapping
   @PreAuthorize("hasAuthority('payment-types.create')")
   public ResponseEntity<PaymentTypeResponse> create(@Valid @RequestBody CreatePaymentTypeRequest request) {
      return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toResponse(createUseCase.createPaymentType(mapper.toCommand(request))));
   }

   @PutMapping("/{id}")
   @PreAuthorize("hasAuthority('payment-types.update')")
   public ResponseEntity<PaymentTypeResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody UpdatePaymentTypeRequest request) {
      return ResponseEntity.ok(mapper.toResponse(updateUseCase.updatePaymentType(id, mapper.toCommand(request))));
   }

   /** Eliminación lógica: va a la papelera */
   @DeleteMapping("/{id}")
   @PreAuthorize("hasAuthority('payment-types.delete')")
   public ResponseEntity<Void> delete(@PathVariable Long id) {
      deleteUseCase.deletePaymentType(id);
      return ResponseEntity.noContent().build();
   }

   @PostMapping("/{id}/restore")
   @PreAuthorize("hasAuthority('payment-types.restore')")
   public ResponseEntity<PaymentTypeResponse> restore(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(restoreUseCase.restorePaymentType(id)));
   }

   @PatchMapping("/{id}/toggle-status")
   @PreAuthorize("hasAuthority('payment-types.update')")
   public ResponseEntity<PaymentTypeResponse> toggleStatus(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleUseCase.togglePaymentTypeStatus(id)));
   }

   @PatchMapping("/{id}/activate")
   @PreAuthorize("hasAuthority('payment-types.update')")
   public ResponseEntity<PaymentTypeResponse> activate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleUseCase.activatePaymentType(id)));
   }

   @PatchMapping("/{id}/deactivate")
   @PreAuthorize("hasAuthority('payment-types.update')")
   public ResponseEntity<PaymentTypeResponse> deactivate(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(toggleUseCase.deactivatePaymentType(id)));
   }
}
