package com.winnersystems.smartparking.payment.application.port.output;

import com.winnersystems.smartparking.payment.application.dto.query.GatewayCharge;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pasarela de pagos con QR dinámico (Yape, Plin, banca). Para la tesis se usa una simulada;
 * una real (Niubiz, Culqi, Izipay...) implementaría este mismo puerto.
 */
public interface PaymentGatewayPort {

   /** Crea el cobro y devuelve su referencia y el QR. Vence en expiresAt */
   GatewayCharge createQrCharge(Long paymentId, BigDecimal amount, String description, LocalDateTime expiresAt);

   /** Anula un cobro sin pagar: el QR deja de aceptar pagos */
   void cancelCharge(String gatewayReference);
}
