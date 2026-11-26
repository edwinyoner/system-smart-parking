package com.winnersystems.smartparking.parking.domain.exception;

/**
 * El usuario no tiene una asignación vigente en ese parqueo (o zona) en el turno actual,
 * y por eso no puede registrar entradas ni salidas allí (regla de negocio 8). Se responde 403.
 */
public class OperatorNotOnDutyException extends ActionNotAllowedException {

   public OperatorNotOnDutyException(String parkingName) {
      super("No estás de turno en " + parkingName
            + ": solo registra entradas y salidas el operador asignado a este parqueo en el turno actual");
   }
}
