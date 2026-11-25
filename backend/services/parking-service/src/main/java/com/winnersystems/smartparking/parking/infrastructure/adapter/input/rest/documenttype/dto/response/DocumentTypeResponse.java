package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.dto.response;

import java.time.LocalDateTime;

/**
 * Respuesta de un tipo de documento.
 *
 * @param lengthLabel      texto de la longitud: "8 dígitos" o "entre 9 y 12 caracteres"
 * @param customersCount   clientes que lo usan: Angular no ofrece eliminarlo si es mayor a 0
 * @param transactionsCount transacciones que lo usaron
 */
public record DocumentTypeResponse(
      Long id,
      String code,
      String name,
      String description,
      String sunatCode,
      int minLength,
      int maxLength,
      boolean numericOnly,
      String lengthLabel,
      Boolean status,
      long customersCount,
      long transactionsCount,
      boolean inUse,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy,
      LocalDateTime deletedAt,
      Long deletedBy
) {
}
