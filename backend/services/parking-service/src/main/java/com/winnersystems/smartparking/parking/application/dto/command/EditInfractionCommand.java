package com.winnersystems.smartparking.parking.application.dto.command;

import java.util.List;

/** Datos editables de una infracción pendiente y sin pagos */
public record EditInfractionCommand(
      String infractionType,
      String severity,
      String description,
      List<String> evidence,
      String notes) {
}
