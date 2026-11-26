package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorAssignmentTest {

   static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

   private static OperatorAssignment create(LocalDate start, LocalDate end) {
      return OperatorAssignment.create(5L, "Ana Ruiz", 1L, null, 2L, start, end, TODAY, 9L);
   }

   @Test
   @DisplayName("crear: empieza hoy por defecto, no en el pasado, y el fin no antecede al inicio")
   void create() {
      OperatorAssignment a = create(null, null);
      assertThat(a.getStartDate()).isEqualTo(TODAY);
      assertThat(a.statusOn(TODAY)).isEqualTo(OperatorAssignment.STATUS_ACTIVE);
      assertThat(a.isParkingWide()).isTrue();
      assertThatThrownBy(() -> create(TODAY.minusDays(1), null)).hasMessageContaining("pasada");
      assertThatThrownBy(() -> create(TODAY.plusDays(5), TODAY.plusDays(4))).hasMessageContaining("anterior");
   }

   @Test
   @DisplayName("estado con fechas inclusivas")
   void status() {
      OperatorAssignment a = create(TODAY.plusDays(1), TODAY.plusDays(3));
      assertThat(a.statusOn(TODAY)).isEqualTo(OperatorAssignment.STATUS_PENDING);
      assertThat(a.statusOn(TODAY.plusDays(3))).isEqualTo(OperatorAssignment.STATUS_ACTIVE);
      assertThat(a.statusOn(TODAY.plusDays(4))).isEqualTo(OperatorAssignment.STATUS_ENDED);
   }

   @Test
   @DisplayName("cruce de fechas, con fin indefinido")
   void overlap() {
      OperatorAssignment a = create(TODAY.plusDays(10), TODAY.plusDays(20));
      assertThat(a.overlapsDates(TODAY.plusDays(20), null)).isTrue();
      assertThat(a.overlapsDates(TODAY.plusDays(21), null)).isFalse();
      assertThat(a.overlapsDates(TODAY, TODAY.plusDays(9))).isFalse();
      assertThat(create(TODAY, null).overlapsDates(TODAY.plusYears(5), null)).isTrue();
   }

   @Test
   @DisplayName("finalizar: si ya empezó queda hasta ayer; si empieza hoy o después se cancela")
   void finish() {
      OperatorAssignment started = create(TODAY, null);
      // al día siguiente se finaliza
      assertThat(started.finish(TODAY.plusDays(3), 1L)).isTrue();
      assertThat(started.getEndDate()).isEqualTo(TODAY.plusDays(2));
      assertThat(started.hasEnded(TODAY.plusDays(3))).isTrue();
      assertThatThrownBy(() -> started.finish(TODAY.plusDays(3), 1L)).hasMessageContaining("finalizada");

      assertThat(create(TODAY, null).finish(TODAY, 1L)).isFalse();
      assertThat(create(TODAY.plusDays(2), null).finish(TODAY, 1L)).isFalse();
   }

   @Test
   @DisplayName("cambiar fechas: si ya empezó, el inicio no cambia y el fin no puede ser pasado")
   void changeDates() {
      OperatorAssignment a = create(TODAY, null);
      LocalDate later = TODAY.plusDays(2);
      assertThatThrownBy(() -> a.changeDates(TODAY.plusDays(1), null, later, 1L)).hasMessageContaining("inicio no cambia");
      assertThatThrownBy(() -> a.changeDates(null, TODAY.plusDays(1), later, 1L)).hasMessageContaining("pasada");
      a.changeDates(null, TODAY.plusDays(30), later, 1L);
      assertThat(a.getEndDate()).isEqualTo(TODAY.plusDays(30));

      OperatorAssignment pending = create(TODAY.plusDays(5), null);
      pending.changeDates(TODAY.plusDays(7), null, TODAY, 1L);
      assertThat(pending.getStartDate()).isEqualTo(TODAY.plusDays(7));
      assertThatThrownBy(() -> pending.changeDates(TODAY.minusDays(1), null, TODAY, 1L))
            .isInstanceOf(ParkingDomainException.class);
   }
}
