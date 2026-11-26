package com.winnersystems.smartparking.parking.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** El espacio que se descuenta al entrar: al azar entre los libres de la zona */
class SpaceAllocatorTest {

   private static Space space(long id, long zoneId) {
      Space s = Space.create(zoneId, "A-" + id, "PERPENDICULAR", null, null, null, null, false, 1L);
      s.setId(id);
      return s;
   }

   @Test
   @DisplayName("solo elige espacios libres, no eliminados y de la zona indicada")
   void onlyFreeSpacesOfZone() {
      Space occupied = space(1, 2);
      occupied.occupy();
      Space deleted = space(2, 2);
      deleted.markAsDeleted(1L);
      Space otherZone = space(3, 9);
      Space free = space(4, 2);

      for (int seed = 0; seed < 20; seed++) {
         assertThat(SpaceAllocator.pickRandomAvailable(List.of(occupied, deleted, otherZone, free), 2L,
               new Random(seed))).contains(free);
      }
   }

   @Test
   @DisplayName("zona llena o sin espacios: no hay espacio")
   void fullZone() {
      Space occupied = space(1, 2);
      occupied.occupy();
      assertThat(SpaceAllocator.pickRandomAvailable(List.of(occupied), 2L, new Random(1))).isEmpty();
      assertThat(SpaceAllocator.pickRandomAvailable(List.of(), 2L, new Random(1))).isEmpty();
      assertThat(SpaceAllocator.pickRandomAvailable(null, 2L, new Random(1))).isEmpty();
   }

   @Test
   @DisplayName("reparte entre los libres (no siempre el primero)")
   void spreadsAmongFree() {
      List<Space> free = List.of(space(1, 2), space(2, 2), space(3, 2), space(4, 2));
      Random random = new Random(42);
      Set<Long> chosen = new HashSet<>();
      for (int i = 0; i < 50; i++) {
         chosen.add(SpaceAllocator.pickRandomAvailable(free, 2L, random).orElseThrow().getId());
      }
      assertThat(chosen).hasSizeGreaterThan(1);
   }
}
