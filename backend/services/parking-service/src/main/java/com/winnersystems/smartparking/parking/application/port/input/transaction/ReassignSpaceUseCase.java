package com.winnersystems.smartparking.parking.application.port.input.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.ReassignSpaceCommand;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDetailDto;

/**
 * Puerto de entrada: cambia el espacio de una estadía en curso (libera el anterior, ocupa el nuevo).
 */
public interface ReassignSpaceUseCase {

   TransactionDetailDto reassignSpace(Long transactionId, ReassignSpaceCommand command);
}
