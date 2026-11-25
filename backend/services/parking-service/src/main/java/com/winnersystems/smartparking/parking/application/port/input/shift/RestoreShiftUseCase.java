package com.winnersystems.smartparking.parking.application.port.input.shift;

import com.winnersystems.smartparking.parking.application.dto.query.ShiftDto;

/**
 * Restaura un turno eliminado por error (papelera).
 */
public interface RestoreShiftUseCase {
   ShiftDto restoreShift(Long id);
}
