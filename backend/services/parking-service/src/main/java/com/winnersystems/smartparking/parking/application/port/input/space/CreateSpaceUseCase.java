package com.winnersystems.smartparking.parking.application.port.input.space;

import com.winnersystems.smartparking.parking.application.dto.command.CreateSpaceCommand;
import com.winnersystems.smartparking.parking.application.dto.query.SpaceDto;

import java.util.List;

/**
 * Puerto de entrada para crear espacios, uno o por lote.
 */
public interface CreateSpaceUseCase {

   SpaceDto createSpace(CreateSpaceCommand command);

   /** Todo o nada: si un espacio es inválido o repetido, no se crea ninguno */
   List<SpaceDto> createSpaces(List<CreateSpaceCommand> commands);
}
