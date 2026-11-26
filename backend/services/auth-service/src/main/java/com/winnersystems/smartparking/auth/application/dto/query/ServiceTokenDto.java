package com.winnersystems.smartparking.auth.application.dto.query;

/** Token de servicio y su duración en segundos */
public record ServiceTokenDto(String accessToken, long expiresInSeconds) {
}
