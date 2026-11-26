package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.dto.request;

import java.time.LocalDate;

/**
 * Cambiar las fechas de una asignación.
 *
 * @param startDate opcional: se mantiene (solo cambia si aún no empezó)
 * @param endDate   null = indefinida
 */
public record UpdateOperatorAssignmentRequest(LocalDate startDate, LocalDate endDate) {
}
