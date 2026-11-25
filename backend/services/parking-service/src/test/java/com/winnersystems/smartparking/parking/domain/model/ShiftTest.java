package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShiftTest {

   private static Shift shift(String start, String end) {
      return Shift.create("Turno", "T1", LocalTime.parse(start), LocalTime.parse(end), null, true, 1L);
   }

   @Nested
   @DisplayName("isWithinShift (al minuto, inicio y fin incluidos)")
   class WithinShift {

      @ParameterizedTest(name = "06:00-13:59 contiene {0}: {1}")
      @CsvSource({"05:59:59,false", "06:00,true", "10:00,true", "13:59,true", "13:59:30,true", "14:00,false"})
      void sameDay(String time, boolean expected) {
         assertThat(shift("06:00", "13:59").isWithinShift(LocalTime.parse(time))).isEqualTo(expected);
      }

      @ParameterizedTest(name = "20:00-05:59 contiene {0}: {1}")
      @CsvSource({"19:59,false", "20:00,true", "23:59:59,true", "00:00,true", "05:59:45,true", "06:00,false", "12:00,false"})
      void crossesMidnight(String time, boolean expected) {
         assertThat(shift("20:00", "05:59").isWithinShift(LocalTime.parse(time))).isEqualTo(expected);
      }

      @Test
      @DisplayName("turnos contiguos no dejan huecos ni se pisan")
      void contiguous() {
         Shift morning = shift("06:00", "13:59");
         Shift afternoon = shift("14:00", "19:59");
         for (int m = 6 * 60; m < 20 * 60; m++) {
            LocalTime t = LocalTime.of(m / 60, m % 60, 30);
            assertThat(morning.isWithinShift(t) ^ afternoon.isWithinShift(t)).as(t.toString()).isTrue();
         }
      }
   }

   @Test
   @DisplayName("duración contando el minuto final")
   void duration() {
      assertThat(shift("06:00", "13:59").getDurationHours()).isEqualTo(8.0);
      assertThat(shift("20:00", "05:59").getDurationHours()).isEqualTo(10.0);
      assertThat(shift("00:00", "23:59").getDurationHours()).isEqualTo(24.0);
      assertThat(shift("20:00", "05:59").crossesMidnight()).isTrue();
   }

   @Nested
   @DisplayName("create")
   class Create {

      @Test
      @DisplayName("normaliza código y textos, guarda auditoría y estado")
      void normalizes() {
         Shift s = Shift.create("  Mañana ", " morning ", LocalTime.of(6, 0, 15), LocalTime.of(13, 59), "  ", true, 9L);
         assertThat(s.getCode()).isEqualTo("MORNING");
         assertThat(s.getName()).isEqualTo("Mañana");
         assertThat(s.getDescription()).isNull();
         assertThat(s.getStartTime()).isEqualTo(LocalTime.of(6, 0));
         assertThat(s.isActive()).isTrue();
         assertThat(s.getCreatedBy()).isEqualTo(9L);
         assertThat(s.getUpdatedBy()).isEqualTo(9L);
         assertThat(s.getCreatedAt()).isNotNull();
      }

      @Test
      @DisplayName("rechaza inicio igual al fin y horas faltantes")
      void invalidSchedule() {
         assertThatThrownBy(() -> shift("08:00", "08:00")).isInstanceOf(ParkingDomainException.class)
               .hasMessageContaining("no pueden ser iguales");
         assertThatThrownBy(() -> Shift.create("A", "B", null, LocalTime.NOON, null, true, 1L))
               .isInstanceOf(ParkingDomainException.class);
      }

      @Test
      @DisplayName("rechaza nombre o código vacíos")
      void blankFields() {
         assertThatThrownBy(() -> Shift.create(" ", "B", LocalTime.MIN, LocalTime.NOON, null, true, 1L))
               .isInstanceOf(ParkingDomainException.class);
         assertThatThrownBy(() -> Shift.create("A", null, LocalTime.MIN, LocalTime.NOON, null, true, 1L))
               .isInstanceOf(ParkingDomainException.class);
      }
   }

   @Test
   @DisplayName("soft delete: desactiva y registra quién y cuándo")
   void softDelete() {
      Shift s = shift("06:00", "13:59");
      s.markAsDeleted(5L);
      assertThat(s.isDeleted()).isTrue();
      assertThat(s.isActive()).isFalse();
      assertThat(s.getDeletedBy()).isEqualTo(5L);
      assertThat(s.getUpdatedBy()).isEqualTo(5L);
      assertThat(s.getDeletedAt()).isNotNull();
   }

   @Test
   @DisplayName("equals y hashCode por ID")
   void equality() {
      Shift a = shift("06:00", "13:59");
      Shift b = shift("06:00", "13:59");
      assertThat(a).isNotEqualTo(b);   // sin ID: mismo código no basta
      a.setId(1L);
      b.setId(1L);
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
   }
}
