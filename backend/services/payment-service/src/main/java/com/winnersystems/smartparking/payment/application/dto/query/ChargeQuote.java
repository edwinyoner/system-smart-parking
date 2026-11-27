package com.winnersystems.smartparking.payment.application.dto.query;

import java.math.BigDecimal;

/**
 * Lo que responde parking-service al preguntar cuánto se debe.
 *
 * @param payable false si no se puede pagar (ya pagado, anulado, sin deuda); reason explica por qué
 */
public record ChargeQuote(
      BigDecimal amountDue,
      String description,
      String plateNumber,
      Long parkingId,
      String parkingName,
      boolean payable,
      String reason
) {
}
