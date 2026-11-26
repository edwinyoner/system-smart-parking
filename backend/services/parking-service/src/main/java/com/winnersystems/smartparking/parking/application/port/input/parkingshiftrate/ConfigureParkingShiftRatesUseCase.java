package com.winnersystems.smartparking.parking.application.port.input.parkingshiftrate;

import com.winnersystems.smartparking.parking.application.dto.command.ConfigureParkingShiftRatesCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingScheduleStatusDto;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingShiftRateDto;

import java.util.List;

/**
 * Horarios y tarifas de un parqueo (valen para todas sus zonas).
 */
public interface ConfigureParkingShiftRatesUseCase {

   /** Reemplaza toda la configuración del parqueo */
   List<ParkingShiftRateDto> configureShiftRates(ConfigureParkingShiftRatesCommand command);

   /** Configuración del parqueo ordenada por hora de inicio (404 si el parqueo no existe) */
   List<ParkingShiftRateDto> getParkingShiftRates(Long parkingId);

   /** Si el parqueo atiende ahora, con qué turno y tarifa */
   ParkingScheduleStatusDto getCurrentSchedule(Long parkingId);

   /** Cambia la tarifa de un turno del parqueo */
   ParkingShiftRateDto changeRate(Long configId, Long rateId);

   void deleteShiftRateConfig(Long configId);

   /** Activa o desactiva un turno del parqueo (al activar se valida que no se solape) */
   ParkingShiftRateDto toggleShiftRateStatus(Long configId);
}
