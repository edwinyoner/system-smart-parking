package com.winnersystems.smartparking.payment.application.dto.query;

import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;
import com.winnersystems.smartparking.payment.domain.model.PaymentStatus;

import java.time.LocalDateTime;

/**
 * Filtros de la lista de pagos; un null no filtra.
 *
 * @param search placa, descripción, N.° de operación o referencia de la pasarela
 */
public record PaymentSearchCriteria(
      PaymentStatus status,
      PaymentConcept concept,
      Long paymentTypeId,
      LocalDateTime from,
      LocalDateTime to,
      String search
) {
}
