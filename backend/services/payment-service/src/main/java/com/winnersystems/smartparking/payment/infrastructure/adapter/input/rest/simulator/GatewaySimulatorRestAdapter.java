package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.simulator;

import com.winnersystems.smartparking.payment.infrastructure.adapter.output.gateway.SimulatedPaymentGateway;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.gateway.SimulatedPaymentGateway.SimulatedCharge;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Hace de "celular del cliente" con la pasarela simulada: pagar o rechazar un QR.
 * Solo existe con payment.gateway.mode = simulated. Exige payments.process.
 */
@RestController
@RequestMapping("/gateway-simulator/charges")
@ConditionalOnProperty(name = "payment.gateway.mode", havingValue = "simulated", matchIfMissing = true)
public class GatewaySimulatorRestAdapter {

   private final SimulatedPaymentGateway gateway;

   public GatewaySimulatorRestAdapter(SimulatedPaymentGateway gateway) {
      this.gateway = gateway;
   }

   @GetMapping("/{reference}")
   @PreAuthorize("hasAuthority('payments.process')")
   public ResponseEntity<SimulatedCharge> get(@PathVariable String reference) {
      return ResponseEntity.ok(gateway.getCharge(reference));
   }

   @PostMapping("/{reference}/pay")
   @PreAuthorize("hasAuthority('payments.process')")
   public ResponseEntity<SimulatedCharge> pay(@PathVariable String reference) {
      return ResponseEntity.ok(gateway.pay(reference));
   }

   @PostMapping("/{reference}/reject")
   @PreAuthorize("hasAuthority('payments.process')")
   public ResponseEntity<SimulatedCharge> reject(@PathVariable String reference,
                                                 @RequestParam(required = false) String reason) {
      return ResponseEntity.ok(gateway.reject(reference, reason));
   }
}
