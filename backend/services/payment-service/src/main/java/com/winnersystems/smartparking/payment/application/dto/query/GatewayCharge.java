package com.winnersystems.smartparking.payment.application.dto.query;

import java.time.LocalDateTime;

/** Cobro creado en la pasarela: su referencia y el contenido del QR */
public record GatewayCharge(String gatewayReference, String qrPayload, LocalDateTime expiresAt) {
}
