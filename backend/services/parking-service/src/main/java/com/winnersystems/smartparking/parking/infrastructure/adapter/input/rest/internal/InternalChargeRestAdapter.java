package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal;

import com.winnersystems.smartparking.parking.application.dto.command.SettleChargeCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ChargeDto;
import com.winnersystems.smartparking.parking.application.port.input.charge.GetChargeUseCase;
import com.winnersystems.smartparking.parking.application.port.input.charge.SettleChargeUseCase;
import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal.dto.ChargeResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal.dto.SettlementRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints para payment-service (red interna; el gateway responde 404 a /internal/**).
 * Exigen el token de servicio: SERVICE_CLIENT más internal.charges.read o internal.charges.settle.
 */
@RestController
@RequestMapping("/internal/charges")
public class InternalChargeRestAdapter {

   private final GetChargeUseCase getChargeUseCase;
   private final SettleChargeUseCase settleChargeUseCase;

   public InternalChargeRestAdapter(GetChargeUseCase getChargeUseCase, SettleChargeUseCase settleChargeUseCase) {
      this.getChargeUseCase = getChargeUseCase;
      this.settleChargeUseCase = settleChargeUseCase;
   }

   /** Cuánto se debe ahora. 404 si la estadía o la multa no existe */
   @GetMapping("/{concept}/{sourceId}")
   @PreAuthorize("hasAuthority('SERVICE_CLIENT') and hasAuthority('internal.charges.read')")
   public ResponseEntity<ChargeResponse> getCharge(@PathVariable ChargeConcept concept, @PathVariable Long sourceId) {
      ChargeDto d = getChargeUseCase.getCharge(concept, sourceId);
      return ResponseEntity.ok(new ChargeResponse(d.concept(), d.sourceId(), d.amountDue(), d.currency(),
            d.description(), d.plateNumber(), d.payable(), d.reason()));
   }

   /** Registra el estado de un pago. Idempotente: el mismo aviso dos veces no cambia nada */
   @PostMapping("/{concept}/{sourceId}/settlement")
   @PreAuthorize("hasAuthority('SERVICE_CLIENT') and hasAuthority('internal.charges.settle')")
   public ResponseEntity<Void> settle(@PathVariable ChargeConcept concept, @PathVariable Long sourceId,
                                      @Valid @RequestBody SettlementRequest r) {
      settleChargeUseCase.settleCharge(new SettleChargeCommand(concept, sourceId, r.paymentId(), r.status(),
            r.amount(), r.refundedAmount(), r.paidAt()));
      return ResponseEntity.noContent().build();
   }
}
