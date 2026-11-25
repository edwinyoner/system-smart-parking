package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;

/**
 * Tipo de documento con sus reglas y su uso.
 *
 * @param customersCount clientes registrados con este tipo (incluye los eliminados: son historial)
 * @param transactionsCount transacciones que lo usaron en la entrada o la salida
 */
public record DocumentTypeDto(
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
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy,
      LocalDateTime deletedAt,
      Long deletedBy
) {
   public boolean inUse() {
      return customersCount > 0 || transactionsCount > 0;
   }
}
