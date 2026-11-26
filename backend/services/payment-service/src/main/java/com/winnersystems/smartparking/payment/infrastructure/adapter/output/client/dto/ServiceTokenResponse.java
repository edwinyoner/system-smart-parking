package com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto;

public record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
