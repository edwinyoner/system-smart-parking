package com.winnersystems.smartparking.parking.application.service.sensor;

import com.winnersystems.smartparking.parking.application.dto.command.SensorReadingCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.SensorReadingResult;
import com.winnersystems.smartparking.parking.application.port.input.sensor.RecordSensorReadingUseCase;
import com.winnersystems.smartparking.parking.application.port.output.SpacePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Space;
import com.winnersystems.smartparking.parking.domain.model.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ocupación real por sensor HC-SR04.
 *
 * Al entrar, el sistema asigna un espacio libre al azar solo para el conteo (el conductor
 * estaciona donde quiere). El sensor corrige ese espacio:
 *
 * - Ve un vehículo en un espacio LIBRE:
 *   a) si en la zona hay una estadía en curso cuyo espacio no lo confirma su sensor
 *      (no tiene sensor o su sensor no ve nada), esa estadía se mueve aquí; el espacio
 *      asignado al azar queda libre. Se elige la entrada más antigua.
 *   b) si no hay ninguna, el espacio se marca ocupado sin estadía (estacionamiento en calle
 *      o vehículo que entró sin registrarse).
 * - Deja de ver el vehículo en un espacio OCUPADO:
 *   a) sin estadía en ese espacio: se libera.
 *   b) con estadía: sigue ocupado hasta la salida (el vehículo puede estar maniobrando);
 *      la estadía queda como candidata para moverse si otro sensor detecta un vehículo.
 * - Espacios en mantenimiento o fuera de servicio: solo se guarda la lectura.
 *
 * Toda lectura actualiza lo que ve el sensor y cuándo (marca de frescura del espacio).
 */
@Service
@Transactional
public class SensorOccupancyService implements RecordSensorReadingUseCase {

   /** Tamaño de página al leer las estadías en curso de la zona (máximo de PageRequest) */
   private static final int PAGE = 100;

   private final SpacePersistencePort spacePort;
   private final TransactionPersistencePort transactionPort;
   private final Clock clock;

   public SensorOccupancyService(SpacePersistencePort spacePort,
                                 TransactionPersistencePort transactionPort,
                                 Clock clock) {
      this.spacePort = spacePort;
      this.transactionPort = transactionPort;
      this.clock = clock;
   }

   @Override
   public SensorReadingResult recordReading(SensorReadingCommand command) {
      if (command == null || command.sensorId() == null || command.sensorId().isBlank()) {
         throw new IllegalArgumentException("Falta el sensor");
      }
      Space space = spacePort.findBySensorIds(List.of(command.sensorId().trim())).stream()
            .filter(s -> !s.isDeleted())
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Ningún espacio tiene el sensor " + command.sensorId()));

      space.recordSensorReading(command.occupied(), LocalDateTime.now(clock));

      if (command.occupied() && space.isAvailable()) {
         return vehicleArrived(space);
      }
      if (!command.occupied() && space.isOccupied()) {
         return vehicleLeft(space);
      }
      spacePort.save(space);
      return result(SensorReadingResult.NO_CHANGE, space, null, "Lectura guardada");
   }

   // ========================= LLEGA UN VEHÍCULO =========================

   private SensorReadingResult vehicleArrived(Space space) {
      List<Transaction> stays = activeStaysInZone(space.getZoneId());
      Optional<Transaction> placeholder = stays.stream()
            .filter(t -> !space.getId().equals(t.getSpaceId()))
            .filter(t -> !confirmedBySensor(t.getSpaceId()))
            .findFirst();

      if (placeholder.isPresent()) {
         Transaction stay = placeholder.get();
         Long previousId = stay.reassignSpace(space.getId(), null);
         transactionPort.save(stay);
         spacePort.findByIdIncludingDeleted(previousId).ifPresent(previous -> {
            previous.release();
            spacePort.save(previous);
         });
         space.occupy();
         spacePort.save(space);
         return result(SensorReadingResult.STAY_MOVED, space, stay.getId(),
               "La estadía " + stay.getId() + " pasó al espacio " + space.getCode());
      }

      space.occupy();
      spacePort.save(space);
      return result(SensorReadingResult.SPACE_OCCUPIED, space, null, "Ocupado sin estadía registrada");
   }

   /** El espacio de una estadía está confirmado si su sensor ve un vehículo */
   private boolean confirmedBySensor(Long spaceId) {
      return spacePort.findById(spaceId).map(Space::sensorDetectsVehicle).orElse(false);
   }

   // ========================= SE VA UN VEHÍCULO =========================

   private SensorReadingResult vehicleLeft(Space space) {
      boolean hasStay = activeStaysInZone(space.getZoneId()).stream()
            .anyMatch(t -> space.getId().equals(t.getSpaceId()));
      if (hasStay) {
         spacePort.save(space);
         return result(SensorReadingResult.NO_CHANGE, space, null,
               "Tiene una estadía en curso: sigue ocupado hasta la salida");
      }
      space.release();
      spacePort.save(space);
      return result(SensorReadingResult.SPACE_RELEASED, space, null, "Espacio libre");
   }

   // ========================= AUXILIARES =========================

   /** Estadías en curso de la zona, de la entrada más antigua a la más nueva */
   private List<Transaction> activeStaysInZone(Long zoneId) {
      List<Transaction> stays = new ArrayList<>();
      int page = 0;
      PageResult<Transaction> result;
      do {
         result = transactionPort.findActiveByZoneId(zoneId, new PageRequest(page++, PAGE, "entryTime", "ASC"));
         stays.addAll(result.content());
      } while (result.hasNext());
      return stays;
   }

   private static SensorReadingResult result(String outcome, Space space, Long transactionId, String message) {
      return new SensorReadingResult(outcome, space.getId(), space.getCode(), space.getStatus(), transactionId, message);
   }
}
