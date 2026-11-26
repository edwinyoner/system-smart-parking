package com.winnersystems.smartparking.parking.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Elige el espacio que se descuenta al entrar un vehículo.
 *
 * El conductor estaciona donde quiera (libre elección): este espacio no se le indica ni se
 * le exige. Sirve para que la disponibilidad, que se calcula desde el estado de cada espacio,
 * sea exacta desde la entrada. Más adelante un sensor o la cámara aérea marcarán el espacio real
 * (Transaction.reassignSpace).
 *
 * Se elige al azar entre los libres de la zona para no concentrar el desgaste ni las
 * correcciones en los primeros espacios.
 */
public final class SpaceAllocator {

   private SpaceAllocator() {
   }

   /**
    * @param candidates espacios de la zona (pueden venir mezclados con ocupados o eliminados)
    * @param zoneId     zona de la entrada
    * @param random     generador (en producción uno real; en pruebas uno con semilla fija)
    * @return un espacio libre de esa zona, o vacío si la zona está llena
    */
   public static Optional<Space> pickRandomAvailable(List<Space> candidates, Long zoneId, RandomGenerator random) {
      if (candidates == null || candidates.isEmpty() || zoneId == null) {
         return Optional.empty();
      }
      List<Space> free = candidates.stream()
            .filter(space -> space != null && space.belongsTo(zoneId) && space.isAvailableForOccupation())
            .toList();
      if (free.isEmpty()) {
         return Optional.empty();
      }
      return Optional.of(free.get(random.nextInt(free.size())));
   }
}
