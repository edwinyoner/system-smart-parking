package com.winnersystems.smartparking.parking.application.dto.command;

/** Datos editables de un tipo de documento (el código no cambia) */
public record UpdateDocumentTypeCommand(
      String name,
      String description,
      String sunatCode,
      Integer minLength,
      Integer maxLength,
      Boolean numericOnly,
      Boolean status
) {
}
