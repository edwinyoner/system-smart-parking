package com.winnersystems.smartparking.parking.application.port.input.shift;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.ShiftDto;

import java.util.List;

public interface ListShiftsUseCase {
   /** deleted = true lista la papelera (turnos eliminados) */
   PagedResponse<ShiftDto> listAllShifts(int page, int size, String search, Boolean status, boolean deleted);
   List<ShiftDto> listAllActiveShifts();
}