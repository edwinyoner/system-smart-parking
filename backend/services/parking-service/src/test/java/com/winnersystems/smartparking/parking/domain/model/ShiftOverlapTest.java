package com.winnersystems.smartparking.parking.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftOverlapTest {

   private static Shift shift(String from, String to) {
      return Shift.create("T" + from, "T" + from.replace(":", ""), LocalTime.parse(from), LocalTime.parse(to), null, true, 1L);
   }

   @Test
   @DisplayName("turnos contiguos no se solapan; compartir un minuto sí")
   void contiguous() {
      assertThat(shift("06:00", "13:59").overlapsWith(shift("14:00", "21:59"))).isFalse();
      assertThat(shift("06:00", "14:00").overlapsWith(shift("14:00", "21:59"))).isTrue();
      assertThat(shift("08:00", "10:00").overlapsWith(shift("06:00", "18:00"))).isTrue();
   }

   @Test
   @DisplayName("turnos que cruzan la medianoche")
   void midnight() {
      Shift night = shift("22:00", "05:59");
      assertThat(night.overlapsWith(shift("06:00", "21:59"))).isFalse();
      assertThat(night.overlapsWith(shift("05:00", "07:00"))).isTrue();
      assertThat(night.overlapsWith(shift("23:00", "23:30"))).isTrue();
      assertThat(night.overlapsWith(shift("20:00", "01:00"))).isTrue();
   }
}
