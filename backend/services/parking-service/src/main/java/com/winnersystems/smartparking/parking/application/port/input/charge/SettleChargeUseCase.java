package com.winnersystems.smartparking.parking.application.port.input.charge;

import com.winnersystems.smartparking.parking.application.dto.command.SettleChargeCommand;

/** Registra lo que confirmó payment-service. Un aviso repetido no tiene efecto */
public interface SettleChargeUseCase {
   void settleCharge(SettleChargeCommand command);
}
