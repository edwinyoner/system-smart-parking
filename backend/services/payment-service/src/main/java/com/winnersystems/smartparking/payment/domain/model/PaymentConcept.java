package com.winnersystems.smartparking.payment.domain.model;

/**
 * Qué se paga. El monto lo calcula parking-service, dueño de la estadía y de la multa;
 * payment-service solo guarda el ID de origen (sourceId).
 */
public enum PaymentConcept {
   /** Estadía (Transaction de parking-service) */
   PARKING_STAY,
   /** Multa (Infraction de parking-service) */
   FINE
}
