package com.winnersystems.smartparking.auth.application.dto.command;

/** Credenciales de un microservicio (client credentials) */
public record IssueServiceTokenCommand(String clientId, String clientSecret) {
}
