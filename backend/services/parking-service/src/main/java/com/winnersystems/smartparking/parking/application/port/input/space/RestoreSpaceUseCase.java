package com.winnersystems.smartparking.parking.application.port.input.space;

import com.winnersystems.smartparking.parking.application.dto.query.SpaceDto;

/**
 * Puerto de entrada para sacar un espacio de la papelera.
 */
public interface RestoreSpaceUseCase {

   SpaceDto restoreSpace(Long spaceId);
}
