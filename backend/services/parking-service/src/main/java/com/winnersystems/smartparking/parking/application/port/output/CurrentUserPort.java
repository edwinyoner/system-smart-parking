package com.winnersystems.smartparking.parking.application.port.output;

import java.util.Optional;

/**
 * Puerto de salida: quién realiza la operación actual.
 *
 * La capa de aplicación lo usa para llenar la auditoría (createdBy, updatedBy, deletedBy)
 * sin depender de Spring Security. El adaptador lo obtiene del JWT validado por el filtro.
 */
public interface CurrentUserPort {

   /**
    * ID del usuario autenticado (claim userId del JWT emitido por auth-service).
    *
    * @return vacío si la petición no tiene un usuario autenticado (p. ej. un proceso interno)
    */
   Optional<Long> getCurrentUserId();

   /**
    * ID del usuario autenticado, obligatorio para operaciones que deben quedar auditadas.
    *
    * @throws IllegalStateException si no hay usuario autenticado
    */
   default Long requireCurrentUserId() {
      return getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("La operación requiere un usuario autenticado"));
   }
}
