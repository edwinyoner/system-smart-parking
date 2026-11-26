package com.winnersystems.smartparking.payment.application.port.input.payment;

import com.winnersystems.smartparking.payment.application.dto.command.GatewayNotification;

/** Procesa el aviso de la pasarela (webhook). Repetir el mismo aviso no tiene efecto */
public interface HandleGatewayNotificationUseCase {
   void handleGatewayNotification(GatewayNotification notification);
}
