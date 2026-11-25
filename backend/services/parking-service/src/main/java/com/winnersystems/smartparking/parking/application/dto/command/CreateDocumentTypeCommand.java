package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * Datos para crear un tipo de documento. Las reglas del número que lleguen en null toman
 * el valor por defecto (1 a 20 caracteres, letras y dígitos).
 */
public record CreateDocumentTypeCommand(
      String code,
      String name,
      String description,
      String sunatCode,
      Integer minLength,
      Integer maxLength,
      Boolean numericOnly,
      Boolean status
) {
}
