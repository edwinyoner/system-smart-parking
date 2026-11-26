package com.winnersystems.smartparking.auth.application.port.input.auth;

import com.winnersystems.smartparking.auth.application.dto.command.IssueServiceTokenCommand;
import com.winnersystems.smartparking.auth.application.dto.query.ServiceTokenDto;

/** Emite un token de corta duración para que un microservicio llame a otro */
public interface IssueServiceTokenUseCase {
   ServiceTokenDto issueServiceToken(IssueServiceTokenCommand command);
}
