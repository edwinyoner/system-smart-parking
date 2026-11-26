package com.winnersystems.smartparking.parking.application.service.parkingshiftrate;

import com.winnersystems.smartparking.parking.application.port.output.ParkingShiftRatePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.RatePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ShiftPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.model.ParkingShiftRate;
import com.winnersystems.smartparking.parking.domain.model.Rate;
import com.winnersystems.smartparking.parking.domain.model.Shift;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decide qué turno y tarifa aplican en un parqueo a una hora dada, y valida que los
 * turnos activos de un parqueo no se solapen.
 *
 * Lo usan la entrada de vehículos (qué tarifa cobrar), la consulta "¿atiende ahora?"
 * y los cambios de configuración o de horario de un turno.
 */
@Component
public class ParkingScheduleResolver {

   private final ParkingShiftRatePersistencePort configPort;
   private final ShiftPersistencePort shiftPort;
   private final RatePersistencePort ratePort;

   public ParkingScheduleResolver(ParkingShiftRatePersistencePort configPort,
                                  ShiftPersistencePort shiftPort,
                                  RatePersistencePort ratePort) {
      this.configPort = configPort;
      this.shiftPort = shiftPort;
      this.ratePort = ratePort;
   }

   /** Configuración con su turno y tarifa ya cargados */
   public record Resolved(ParkingShiftRate config, Shift shift, Rate rate) {
   }

   /**
    * Turno y tarifa que aplican a esa hora. Vacío si el parqueo no atiende: ninguna
    * configuración activa con turno y tarifa usables cubre la hora.
    */
   public Optional<Resolved> resolve(Long parkingId, LocalDateTime at) {
      return usableConfigs(parkingId).stream()
            .filter(r -> r.shift().isWithinShift(at.toLocalTime()))
            // Con turnos sin solape hay a lo sumo uno; el orden solo deja el resultado estable
            .min(Comparator.comparing((Resolved r) -> r.shift().getStartTime()));
   }

   /** Configuraciones activas del parqueo cuyo turno y tarifa se pueden usar */
   public List<Resolved> usableConfigs(Long parkingId) {
      List<Resolved> result = new ArrayList<>();
      for (ParkingShiftRate config : configPort.findByParkingId(parkingId)) {
         if (!config.isActive()) {
            continue;
         }
         Optional<Shift> shift = shiftPort.findById(config.getShiftId()).filter(Shift::isUsable);
         Optional<Rate> rate = ratePort.findById(config.getRateId()).filter(Rate::isUsable);
         if (shift.isPresent() && rate.isPresent()) {
            result.add(new Resolved(config, shift.get(), rate.get()));
         }
      }
      return result;
   }

   /**
    * Lanza 409 si algún par de turnos de la lista se solapa.
    *
    * @param shifts turnos que quedarían activos a la vez en un parqueo
    */
   public static void ensureNoOverlap(List<Shift> shifts) {
      for (int i = 0; i < shifts.size(); i++) {
         for (int j = i + 1; j < shifts.size(); j++) {
            Shift a = shifts.get(i);
            Shift b = shifts.get(j);
            if (a.overlapsWith(b)) {
               throw new ResourceConflictException("Los turnos " + a.getName() + " (" + a.getTimeRange() + ") y "
                     + b.getName() + " (" + b.getTimeRange() + ") se solapan: en un parqueo cada hora "
                     + "debe tener una sola tarifa. Desactiva uno o ajusta sus horarios.");
            }
         }
      }
   }
}
