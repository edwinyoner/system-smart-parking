package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * Cambia el espacio de una estadía en curso (el conductor estacionó en otro lugar).
 *
 * @param spaceId espacio nuevo, libre y de la misma zona
 */
public record ReassignSpaceCommand(Long spaceId) {
}
