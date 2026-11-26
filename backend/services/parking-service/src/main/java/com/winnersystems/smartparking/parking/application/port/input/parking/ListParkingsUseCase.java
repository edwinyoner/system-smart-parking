package com.winnersystems.smartparking.parking.application.port.input.parking;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingDto;

import java.util.List;

/**
 * Puerto de entrada para listar parqueos.
 */
public interface ListParkingsUseCase {

   /** @param deleted true = papelera */
   PagedResponse<ParkingDto> listAllParkings(int pageNumber, int pageSize, String search, String status, boolean deleted);

   List<ParkingDto> listAllActiveParkings();
}
