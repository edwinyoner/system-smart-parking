package com.winnersystems.smartparking.parking.application.dto.command;

import java.util.List;

/**
 * Horarios y tarifas de un parqueo: reemplaza toda su configuración.
 *
 * @param configurations un elemento por turno (lista vacía = el parqueo no atiende en ningún horario)
 */
public record ConfigureParkingShiftRatesCommand(Long parkingId, List<Item> configurations) {

   /** @param status null = activo */
   public record Item(Long shiftId, Long rateId, Boolean status) {
   }
}
