package com.winnersystems.smartparking.parking.application.port.input.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.RecordEntryCommand;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDetailDto;

/**
 * Puerto de entrada: entrada MANUAL de un vehículo, registrada por un operador desde el panel.
 *
 * Reglas (las mismas que la entrada por cámara):
 * - Parqueo y zona operativos; la zona es del parqueo.
 * - El operador está de turno en ese parqueo o zona (o tiene transactions.any-parking).
 * - Un turno con tarifa vigente a esta hora (si no, el parqueo no atiende).
 * - El vehículo no tiene otra estadía en curso.
 * - Espacio: el indicado (libre y de la zona) o uno libre al azar; zona llena: 409.
 * - Documento opcional, salvo que el parqueo lo exija.
 */
public interface RecordEntryUseCase {

   /**
    * Registra la entrada de un vehículo al estacionamiento.
    *
    * @param command datos de la entrada
    * @return TransactionDetailDto con todos los detalles de la transacción creada
    *
    * @throws com.winnersystems.smartparking.parking.domain.exception.VehicleAlreadyInsideException
    *         si el vehículo ya tiene una transacción activa
    * @throws com.winnersystems.smartparking.parking.domain.exception.SpaceNotAvailableException
    *         si el espacio no está disponible
    * @throws com.winnersystems.smartparking.parking.domain.exception.ZoneNotOperationalException
    *         si la zona no está operativa
    */
   TransactionDetailDto recordEntry(RecordEntryCommand command);
}