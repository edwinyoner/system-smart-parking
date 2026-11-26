package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;

/**
 * Estadía en curso reducida a lo que necesita el mapa de ocupación: en qué espacio está
 * y qué vehículo es. Se lee en una sola consulta por parqueo.
 */
public record ActiveStayView(
      Long transactionId,
      Long spaceId,
      String plateNumber,
      LocalDateTime entryTime
) {}
