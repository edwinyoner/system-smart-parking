package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment;

import com.winnersystems.smartparking.payment.application.dto.command.RefundPaymentCommand;
import com.winnersystems.smartparking.payment.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentSearchCriteria;
import com.winnersystems.smartparking.payment.application.port.input.payment.*;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;
import com.winnersystems.smartparking.payment.domain.model.PaymentStatus;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.request.CancelPaymentRequest;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.request.CreatePaymentRequest;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.request.RefundPaymentRequest;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.response.PaymentResponse;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.mapper.PaymentRestMapper;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Pagos de estadías y multas. Permisos: payments.process (cobrar), payments.view (ver),
 * payments.cancel (anular un QR pendiente) y payments.refund (devolver).
 *
 * Por ahora cobra el operador desde el panel; la pantalla de salida, el kiosco (API key del dispositivo)
 * y la app (login de conductores) usarán este mismo flujo cuando existan.
 */
@RestController
@RequestMapping("/payments")
public class PaymentRestAdapter {

   private static final int MAX_PAGE_SIZE = 100;

   private final CreatePaymentUseCase createUseCase;
   private final GetPaymentUseCase getUseCase;
   private final ListPaymentsUseCase listUseCase;
   private final CancelPaymentUseCase cancelUseCase;
   private final RefundPaymentUseCase refundUseCase;
   private final PaymentRestMapper mapper;

   public PaymentRestAdapter(CreatePaymentUseCase createUseCase, GetPaymentUseCase getUseCase,
                             ListPaymentsUseCase listUseCase, CancelPaymentUseCase cancelUseCase,
                             RefundPaymentUseCase refundUseCase, PaymentRestMapper mapper) {
      this.createUseCase = createUseCase;
      this.getUseCase = getUseCase;
      this.listUseCase = listUseCase;
      this.cancelUseCase = cancelUseCase;
      this.refundUseCase = refundUseCase;
      this.mapper = mapper;
   }

   /** Efectivo: 201 con el pago COMPLETED. QR: 201 con el pago PENDING y su QR */
   @PostMapping
   @PreAuthorize("hasAuthority('payments.process')")
   public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
      return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapper.toResponse(createUseCase.createPayment(mapper.toCommand(request))));
   }

   /**
    * Lista paginada, la más reciente primero. from y to son fechas (yyyy-MM-dd), ambas incluidas.
    * search: placa, descripción, N.° de operación o referencia de la pasarela.
    */
   @GetMapping
   @PreAuthorize("hasAuthority('payments.view')")
   public ResponseEntity<PagedResponse<PaymentResponse>> list(
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "10") int size,
         @RequestParam(required = false) PaymentStatus status,
         @RequestParam(required = false) PaymentConcept concept,
         @RequestParam(required = false) Long paymentTypeId,
         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
         @RequestParam(required = false) String search) {
      PaymentSearchCriteria criteria = new PaymentSearchCriteria(status, concept, paymentTypeId,
            from == null ? null : from.atStartOfDay(),
            to == null ? null : to.plusDays(1).atStartOfDay(),
            search);
      int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
      return ResponseEntity.ok(listUseCase.listPayments(criteria, Math.max(page, 0), safeSize).map(mapper::toResponse));
   }

   /** También sirve para consultar si un QR ya se pagó (la pantalla lo consulta cada pocos segundos) */
   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('payments.view') or hasAuthority('payments.process')")
   public ResponseEntity<PaymentResponse> getById(@PathVariable Long id) {
      return ResponseEntity.ok(mapper.toResponse(getUseCase.getPaymentById(id)));
   }

   /** Pagos de una estadía o una multa, el más reciente primero */
   @GetMapping("/by-source/{concept}/{sourceId}")
   @PreAuthorize("hasAuthority('payments.view') or hasAuthority('payments.process')")
   public ResponseEntity<List<PaymentResponse>> listBySource(@PathVariable PaymentConcept concept,
                                                             @PathVariable Long sourceId) {
      return ResponseEntity.ok(getUseCase.listPaymentsBySource(concept, sourceId).stream()
            .map(mapper::toResponse).toList());
   }

   @PostMapping("/{id}/cancel")
   @PreAuthorize("hasAuthority('payments.cancel')")
   public ResponseEntity<PaymentResponse> cancel(@PathVariable Long id, @Valid @RequestBody CancelPaymentRequest request) {
      return ResponseEntity.ok(mapper.toResponse(cancelUseCase.cancelPayment(id, request.getReason())));
   }

   @PostMapping("/{id}/refund")
   @PreAuthorize("hasAuthority('payments.refund')")
   public ResponseEntity<PaymentResponse> refund(@PathVariable Long id, @Valid @RequestBody RefundPaymentRequest request) {
      return ResponseEntity.ok(mapper.toResponse(
            refundUseCase.refundPayment(id, new RefundPaymentCommand(request.getAmount(), request.getReason()))));
   }
}
