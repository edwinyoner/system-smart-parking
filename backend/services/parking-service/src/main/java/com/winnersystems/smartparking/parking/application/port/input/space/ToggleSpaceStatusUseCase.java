package com.winnersystems.smartparking.parking.application.port.input.space;

import com.winnersystems.smartparking.parking.application.dto.query.SpaceDto;

/**
 * Puerto de entrada para cambiar el estado de un espacio.
 */
public interface ToggleSpaceStatusUseCase {

   /** Alterna entre libre y fuera de servicio (no aplica a un espacio ocupado) */
   SpaceDto toggleSpaceStatus(Long spaceId);

   /** Ocupación detectada sin transacción (cámara, sensor u operador) */
   SpaceDto markAsOccupied(Long spaceId);

   /** Libera un espacio ocupado sin transacción (409 si hay un vehículo registrado dentro) */
   SpaceDto markAsAvailable(Long spaceId);

   SpaceDto setInMaintenance(Long spaceId);

   SpaceDto setOutOfService(Long spaceId);
}
