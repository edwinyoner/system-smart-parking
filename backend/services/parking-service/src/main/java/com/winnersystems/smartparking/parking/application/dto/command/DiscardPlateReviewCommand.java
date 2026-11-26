package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * El operador descarta una lectura (no era un vehículo, foto ilegible, falsa alarma...).
 *
 * @param reason motivo (obligatorio)
 */
public record DiscardPlateReviewCommand(String reason) {
}
