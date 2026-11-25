package com.winnersystems.smartparking.auth.infrastructure.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Responde 401 con el motivo cuando no hay una sesión válida.
 * Por defecto Spring respondería 403, que el cliente entiende como falta de permisos.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public class SessionEntryPoint implements AuthenticationEntryPoint {

   private final ObjectMapper objectMapper = new ObjectMapper();

   @Override
   public void commence(
         HttpServletRequest request,
         HttpServletResponse response,
         AuthenticationException authException) throws IOException {

      SessionEndReason reason =
            request.getAttribute(SessionEndReason.REQUEST_ATTRIBUTE) instanceof SessionEndReason found
                  ? found
                  : SessionEndReason.SESSION_EXPIRED;

      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      objectMapper.writeValue(response.getWriter(), Map.of(
            "status", HttpServletResponse.SC_UNAUTHORIZED,
            "code", reason.name(),
            "message", reason.getMessage()));
   }
}
