package com.winnersystems.smartparking.payment.infrastructure.adapter.output.client;

import com.winnersystems.smartparking.payment.application.dto.query.ChargeQuote;
import com.winnersystems.smartparking.payment.application.port.output.ChargePort;
import com.winnersystems.smartparking.payment.domain.exception.ExternalServiceException;
import com.winnersystems.smartparking.payment.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.payment.domain.model.Payment;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto.ChargeResponse;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto.SettlementRequest;
import feign.FeignException;
import org.springframework.stereotype.Component;

/** ChargePort sobre los endpoints internos de parking-service */
@Component
public class ParkingChargeAdapter implements ChargePort {

   private final ParkingServiceClient client;
   private final ServiceTokenProvider tokenProvider;

   public ParkingChargeAdapter(ParkingServiceClient client, ServiceTokenProvider tokenProvider) {
      this.client = client;
      this.tokenProvider = tokenProvider;
   }

   @Override
   public ChargeQuote getCharge(PaymentConcept concept, Long sourceId) {
      try {
         ChargeResponse r = client.getCharge(concept.name(), sourceId);
         return new ChargeQuote(r.amountDue(), r.description(), r.plateNumber(), r.payable(), r.reason());
      } catch (FeignException.NotFound e) {
         throw new ResourceNotFoundException(
               (concept == PaymentConcept.FINE ? "Multa" : "Estadía") + " no encontrada con ID: " + sourceId);
      } catch (FeignException.Unauthorized e) {
         tokenProvider.invalidate();
         throw new ExternalServiceException("parking-service rechazó el token de servicio", e);
      } catch (FeignException e) {
         throw new ExternalServiceException("parking-service no respondió al consultar el cobro", e);
      }
   }

   @Override
   public void notifySettlement(Payment p) {
      try {
         client.settle(p.getConcept().name(), p.getSourceId(), new SettlementRequest(
               p.getId(), p.getStatus().name(), p.getAmount(), p.getRefundedAmount(), p.getPaidAt(),
               p.getPaymentTypeCode(), p.getPaymentTypeName(), p.getOperationNumber()));
      } catch (FeignException.Unauthorized e) {
         tokenProvider.invalidate();
         throw e;
      }
   }
}
