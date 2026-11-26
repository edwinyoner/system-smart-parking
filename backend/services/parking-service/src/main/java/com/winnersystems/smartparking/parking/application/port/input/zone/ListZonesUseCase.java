package com.winnersystems.smartparking.parking.application.port.input.zone;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneDto;

import java.util.List;

/**
 * Puerto de entrada para listar zonas.
 */
public interface ListZonesUseCase {

   /**
    * @param parkingId filtra por parqueo (null = todos)
    * @param deleted   true = papelera
    */
   PagedResponse<ZoneDto> listAllZones(int pageNumber, int pageSize, String search, String status,
                                       Long parkingId, boolean deleted);

   /** Zonas que reciben vehículos (parqueo y zona activos). parkingId null = todas */
   List<ZoneDto> listAllActiveZones(Long parkingId);

   /** Todas las zonas vigentes de un parqueo, en cualquier estado (404 si el parqueo no existe) */
   List<ZoneDto> listZonesByParking(Long parkingId);
}
