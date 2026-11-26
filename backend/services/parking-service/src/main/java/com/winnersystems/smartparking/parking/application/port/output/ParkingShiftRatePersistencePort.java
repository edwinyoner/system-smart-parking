package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.domain.model.ParkingShiftRate;

import java.util.List;
import java.util.Optional;

public interface ParkingShiftRatePersistencePort {

   ParkingShiftRate save(ParkingShiftRate parkingShiftRate);

   List<ParkingShiftRate> saveAll(List<ParkingShiftRate> configs);

   Optional<ParkingShiftRate> findById(Long id);

   List<ParkingShiftRate> findByParkingId(Long parkingId);

   /** Configuraciones activas que usan el turno (para validar un cambio de horario) */
   List<ParkingShiftRate> findActiveByShiftId(Long shiftId);

   void deleteByParkingId(Long parkingId);

   void deleteById(Long id);

   /**
    * Nombres de los parqueos (no eliminados) que usan el turno en sus tarifas.
    * Sirve para impedir eliminar un turno en uso y para avisar antes de desactivarlo.
    */
   List<String> findParkingNamesUsingShift(Long shiftId);

   /** Nombres de los parqueos (no eliminados) que usan la tarifa en algún turno */
   List<String> findParkingNamesUsingRate(Long rateId);
}
