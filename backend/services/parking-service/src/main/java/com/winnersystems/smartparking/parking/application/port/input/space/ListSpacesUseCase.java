package com.winnersystems.smartparking.parking.application.port.input.space;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.SpaceDto;

import java.util.List;

/**
 * Puerto de entrada para listar espacios.
 */
public interface ListSpacesUseCase {

   /** @param deleted true = papelera */
   PagedResponse<SpaceDto> listAllSpaces(int pageNumber, int pageSize, String search, String status, String type,
                                         Long zoneId, Long parkingId, boolean deleted);

   /** Todos los espacios vigentes de una zona (404 si la zona no existe) */
   List<SpaceDto> listSpacesByZone(Long zoneId);

   /** Espacios que se pueden asignar en una entrada: libres, en zona y parqueo activos */
   List<SpaceDto> listAvailableByZone(Long zoneId);
}
