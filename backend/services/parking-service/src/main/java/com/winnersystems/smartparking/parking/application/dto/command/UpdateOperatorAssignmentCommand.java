package com.winnersystems.smartparking.parking.application.dto.command;

import java.time.LocalDate;

/**
 * Cambiar las fechas de una asignación (el operador, el parqueo, la zona y el turno no
 * cambian: para eso se finaliza y se crea otra).
 *
 * @param startDate null = se mantiene
 * @param endDate   null = indefinida
 */
public record UpdateOperatorAssignmentCommand(LocalDate startDate, LocalDate endDate) {
}
