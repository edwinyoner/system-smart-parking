package com.winnersystems.smartparking.auth.application.port.output;

import com.winnersystems.smartparking.auth.domain.model.ServiceClient;

import java.util.Optional;

/** Clientes de servicio registrados (config service-clients en auth-service.yml) */
public interface ServiceClientPort {

   /**
    * Devuelve el cliente solo si el secreto coincide. Un cliente sin secreto configurado
    * queda deshabilitado y nunca coincide.
    */
   Optional<ServiceClient> authenticate(String clientId, String clientSecret);
}
