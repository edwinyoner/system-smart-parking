package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.auth.dto.response;

/** Token de servicio (Bearer) y segundos hasta que vence */
public record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
