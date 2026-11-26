package com.winnersystems.smartparking.payment.application.port.output;

import com.winnersystems.smartparking.payment.application.dto.query.ChargeQuote;
import com.winnersystems.smartparking.payment.domain.model.Payment;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;

/**
 * parking-service: dueño de las estadías y las multas. Las llamadas usan el token de servicio.
 */
public interface ChargePort {

   /**
    * Cuánto se debe ahora por una estadía o una multa.
    *
    * @throws com.winnersystems.smartparking.payment.domain.exception.ResourceNotFoundException si no existe
    */
   ChargeQuote getCharge(PaymentConcept concept, Long sourceId);

   /**
    * Avisa el estado actual del pago (pagado o devuelto). parking-service lo registra una sola
    * vez por pago, así que repetir el aviso no tiene efecto.
    *
    * @throws RuntimeException si parking-service no responde: el aviso se reintenta después
    */
   void notifySettlement(Payment payment);
}
