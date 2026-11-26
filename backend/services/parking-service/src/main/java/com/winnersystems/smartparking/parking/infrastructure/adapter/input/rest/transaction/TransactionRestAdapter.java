package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.CancelTransactionCommand;
import com.winnersystems.smartparking.parking.application.dto.command.ReassignSpaceCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateTransactionCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDetailDto;
import com.winnersystems.smartparking.parking.application.port.input.transaction.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.response.ActiveTransactionResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.response.TransactionDetailResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.response.TransactionResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.mapper.TransactionRestMapper;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Estadías (entradas y salidas) desde el panel.
 *
 * Permisos: transactions.entry (entrar, cambiar espacio), transactions.exit (salir),
 * transactions.view (consultas), transactions.cancel (cancelar). Además, entrar y salir exige
 * estar de turno en ese parqueo (lo comprueba el servicio, 403 si no).
 * La cámara no usa estos endpoints: entra por /internal/captures con token de servicio.
 */
@RestController
@RequestMapping("/transactions")
public class TransactionRestAdapter {

   /** Campos por los que se puede ordenar el historial (otro campo daría 500 en la consulta) */
   private static final Set<String> SORTABLE = Set.of("entryTime", "exitTime", "createdAt", "totalAmount");

   private final RecordEntryUseCase recordEntryUseCase;
   private final RecordExitUseCase recordExitUseCase;
   private final GetTransactionUseCase getTransactionUseCase;
   private final ListActiveTransactionsUseCase listActiveTransactionsUseCase;
   private final ListTransactionsUseCase listTransactionsUseCase;
   private final CancelTransactionUseCase cancelTransactionUseCase;
   private final UpdateTransactionUseCase updateTransactionUseCase;
   private final ReassignSpaceUseCase reassignSpaceUseCase;
   private final TransactionRestMapper mapper;

   public TransactionRestAdapter(RecordEntryUseCase recordEntryUseCase,
                                 RecordExitUseCase recordExitUseCase,
                                 GetTransactionUseCase getTransactionUseCase,
                                 ListActiveTransactionsUseCase listActiveTransactionsUseCase,
                                 ListTransactionsUseCase listTransactionsUseCase,
                                 CancelTransactionUseCase cancelTransactionUseCase,
                                 UpdateTransactionUseCase updateTransactionUseCase,
                                 ReassignSpaceUseCase reassignSpaceUseCase,
                                 TransactionRestMapper mapper) {
      this.recordEntryUseCase = recordEntryUseCase;
      this.recordExitUseCase = recordExitUseCase;
      this.getTransactionUseCase = getTransactionUseCase;
      this.listActiveTransactionsUseCase = listActiveTransactionsUseCase;
      this.listTransactionsUseCase = listTransactionsUseCase;
      this.cancelTransactionUseCase = cancelTransactionUseCase;
      this.updateTransactionUseCase = updateTransactionUseCase;
      this.reassignSpaceUseCase = reassignSpaceUseCase;
      this.mapper = mapper;
   }

   // ========================= ENTRADA, SALIDA Y CAMBIOS =========================

   @PostMapping("/entry")
   @PreAuthorize("hasAuthority('transactions.entry')")
   public ResponseEntity<TransactionDetailResponse> recordEntry(@Valid @RequestBody RecordEntryRequest request) {
      TransactionDetailDto dto = recordEntryUseCase.recordEntry(mapper.toCommand(request));
      return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDetailResponse(dto));
   }

   /** 409 si falta pagar: el panel cobra y vuelve a intentar */
   @PostMapping("/exit")
   @PreAuthorize("hasAuthority('transactions.exit')")
   public ResponseEntity<TransactionDetailResponse> recordExit(@Valid @RequestBody RecordExitRequest request) {
      TransactionDetailDto dto = recordExitUseCase.recordExit(mapper.toCommand(request));
      return ResponseEntity.ok(mapper.toDetailResponse(dto));
   }

   /** El vehículo estacionó en otro espacio de la misma zona */
   @PatchMapping("/{id}/space")
   @PreAuthorize("hasAuthority('transactions.entry')")
   public ResponseEntity<TransactionDetailResponse> reassignSpace(@PathVariable Long id,
                                                                  @Valid @RequestBody ReassignSpaceRequest request) {
      TransactionDetailDto dto = reassignSpaceUseCase.reassignSpace(id, new ReassignSpaceCommand(request.spaceId()));
      return ResponseEntity.ok(mapper.toDetailResponse(dto));
   }

   @PatchMapping("/{id}/notes")
   @PreAuthorize("hasAnyAuthority('transactions.entry', 'transactions.exit')")
   public ResponseEntity<TransactionResponse> updateNotes(@PathVariable Long id,
                                                          @Valid @RequestBody UpdateTransactionNotesRequest request) {
      return ResponseEntity.ok(mapper.toResponse(
            updateTransactionUseCase.updateTransaction(id, new UpdateTransactionCommand(request.notes()))));
   }

   /** Solo una estadía en curso y sin pagos; libera el espacio */
   @PostMapping("/{id}/cancel")
   @PreAuthorize("hasAuthority('transactions.cancel')")
   public ResponseEntity<TransactionResponse> cancel(@PathVariable Long id,
                                                     @Valid @RequestBody CancelTransactionRequest request) {
      return ResponseEntity.ok(mapper.toResponse(
            cancelTransactionUseCase.cancelTransaction(id, new CancelTransactionCommand(request.reason()))));
   }

   // ========================= CONSULTAS =========================

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<TransactionDetailResponse> getById(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toDetailResponse(getTransactionUseCase.getTransactionById(id)));
   }

   @GetMapping("/active/plate/{plateNumber}")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<TransactionDetailResponse> getActiveByPlate(@PathVariable String plateNumber) {
      TransactionDetailDto dto = getTransactionUseCase.getActiveTransactionByPlate(plateNumber);
      return dto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(mapper.toDetailResponse(dto));
   }

   // ========================= EN CURSO =========================

   @GetMapping("/active")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<ActiveTransactionResponse>> listActive(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(listActiveTransactionsUseCase.listAllActiveTransactions(page, size)
            .map(mapper::toActiveResponse));
   }

   @GetMapping("/active/zone/{zoneId}")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<ActiveTransactionResponse>> listActiveByZone(
         @PathVariable Long zoneId,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(listActiveTransactionsUseCase.listActiveTransactionsByZone(zoneId, page, size)
            .map(mapper::toActiveResponse));
   }

   @GetMapping("/active/search")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<ActiveTransactionResponse>> searchActiveByPlate(
         @RequestParam String plateNumber,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(listActiveTransactionsUseCase.searchActiveTransactionsByPlate(plateNumber, page, size)
            .map(mapper::toActiveResponse));
   }

   @GetMapping("/active/overdue")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<ActiveTransactionResponse>> listOverdue(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(listActiveTransactionsUseCase.listOverdueTransactions(page, size)
            .map(mapper::toActiveResponse));
   }

   // ========================= HISTORIAL =========================

   @GetMapping
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<TransactionResponse>> listAll(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size,
         @RequestParam(defaultValue = "createdAt") String sortBy,
         @RequestParam(defaultValue = "DESC") String sortDirection) {
      if (!SORTABLE.contains(sortBy)) {
         throw new IllegalArgumentException("No se puede ordenar por " + sortBy + "; usa uno de " + SORTABLE);
      }
      return ResponseEntity.ok(listTransactionsUseCase.listAllTransactions(page, size, sortBy, sortDirection)
            .map(mapper::toResponse));
   }

   @GetMapping("/date-range")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<TransactionResponse>> listByDateRange(
         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(listTransactionsUseCase.listTransactionsByDateRange(startDate, endDate, page, size)
            .map(mapper::toResponse));
   }

   @GetMapping("/status/{status}")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<TransactionResponse>> listByStatus(
         @PathVariable String status,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(listTransactionsUseCase.listTransactionsByStatus(status.toUpperCase(), page, size)
            .map(mapper::toResponse));
   }

   @GetMapping("/payment-status/{paymentStatus}")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<TransactionResponse>> listByPaymentStatus(
         @PathVariable String paymentStatus,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(listTransactionsUseCase
            .listTransactionsByPaymentStatus(paymentStatus.toUpperCase(), page, size)
            .map(mapper::toResponse));
   }

   @GetMapping("/zone/{zoneId}")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<TransactionResponse>> listByZone(
         @PathVariable Long zoneId,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(listTransactionsUseCase.listTransactionsByZone(zoneId, page, size)
            .map(mapper::toResponse));
   }

   @GetMapping("/search")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<TransactionResponse>> searchByPlate(
         @RequestParam String plateNumber,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(listTransactionsUseCase.searchTransactionsByPlate(plateNumber, page, size)
            .map(mapper::toResponse));
   }
}
