package com.winnersystems.smartparking.auth.infrastructure.config.security;

/**
 * Motivo por el que un token con firma válida dejó de dar acceso.
 * El cliente usa el nombre del motivo para elegir el mensaje.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public enum SessionEndReason {

   ACCOUNT_DISABLED("Tu usuario fue desactivado. Comunícate con el administrador."),
   ACCOUNT_DELETED("Tu usuario fue dado de baja. Comunícate con el administrador."),
   EMAIL_CHANGED("Tu correo de acceso fue cambiado. Verifica el nuevo correo e inicia sesión con él."),
   PASSWORD_CHANGED("Tu contraseña fue cambiada. Inicia sesión con la nueva."),
   ROLE_REMOVED("Ya no tienes el rol con el que iniciaste sesión. Vuelve a entrar con otro."),
   SESSION_EXPIRED("Tu sesión ha expirado. Por favor, inicia sesión nuevamente.");

   /** Atributo de la petición donde el filtro JWT deja el motivo */
   static final String REQUEST_ATTRIBUTE = SessionEndReason.class.getName();

   private final String message;

   SessionEndReason(String message) {
      this.message = message;
   }

   public String getMessage() {
      return message;
   }
}
