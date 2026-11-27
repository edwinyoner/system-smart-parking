package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.infraction.dto.request;

import jakarta.validation.constraints.Size;

/** Motivo de la anulación (obligatorio, lo valida el dominio) o comentario de la amonestación (opcional) */
public record ResolveInfractionRequest(@Size(max = 500) String reason) {
}
