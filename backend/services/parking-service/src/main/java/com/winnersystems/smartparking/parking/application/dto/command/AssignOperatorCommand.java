package com.winnersystems.smartparking.parking.application.dto.command;

import java.time.LocalDate;

/**
 * Asignar un operador a un parqueo (o a una de sus zonas) en un turno.
 *
 * @param zoneId    null = todo el parqueo
 * @param startDate null = hoy
 * @param endDate   null = indefinida
 */
public record AssignOperatorCommand(Long operatorId, Long parkingId, Long zoneId, Long shiftId,
                                    LocalDate startDate, LocalDate endDate) {
}
