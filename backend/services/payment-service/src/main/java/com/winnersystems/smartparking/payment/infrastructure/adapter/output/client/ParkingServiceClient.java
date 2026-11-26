package com.winnersystems.smartparking.payment.infrastructure.adapter.output.client;

import com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto.ChargeResponse;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto.SettlementRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Endpoints internos de parking-service (/internal/**): exigen el token de servicio y no pasan por el gateway.
 * concept: PARKING_STAY o FINE.
 */
@FeignClient(name = "parking-service", contextId = "parkingChargesClient", configuration = ParkingClientConfig.class)
public interface ParkingServiceClient {

   @GetMapping("/internal/charges/{concept}/{sourceId}")
   ChargeResponse getCharge(@PathVariable("concept") String concept, @PathVariable("sourceId") Long sourceId);

   @PostMapping("/internal/charges/{concept}/{sourceId}/settlement")
   void settle(@PathVariable("concept") String concept, @PathVariable("sourceId") Long sourceId,
               @RequestBody SettlementRequest request);
}
