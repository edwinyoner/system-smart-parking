package com.winnersystems.smartparking.parking.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InfractionFineSettlementTest {

   private static Infraction withFine(String amount) {
      Infraction i = new Infraction();
      i.setId(7L);
      i.setInfractionCode("INF-2026-000007");
      i.assignFine(new BigDecimal(amount));
      return i;
   }

   @Test
   @DisplayName("pagada por completo: finePaid y resuelta como PAID; parcial: falta la diferencia")
   void paid() {
      Infraction i = withFine("50.00");
      i.applyFineSettlement(new BigDecimal("20.00"), LocalDateTime.now(), 1L);
      assertThat(i.isFinePaid()).isFalse();
      assertThat(i.getFineAmountDue()).isEqualByComparingTo("30.00");
      assertThat(i.isFinePayable()).isTrue();

      i.applyFineSettlement(new BigDecimal("50.00"), LocalDateTime.now(), 2L);
      assertThat(i.isFinePaid()).isTrue();
      assertThat(i.isResolved()).isTrue();
      assertThat(i.getResolutionType()).isEqualTo(Infraction.RESOLUTION_PAID);
      assertThat(i.getFinePaymentReference()).isEqualTo("PAGO-2");
      assertThat(i.isFinePayable()).isFalse();
   }

   @Test
   @DisplayName("si se devuelve el pago, la multa vuelve a estar pendiente")
   void refunded() {
      Infraction i = withFine("50.00");
      i.applyFineSettlement(new BigDecimal("50.00"), LocalDateTime.now(), 2L);
      i.applyFineSettlement(BigDecimal.ZERO, null, null);
      assertThat(i.isFinePaid()).isFalse();
      assertThat(i.isPending()).isTrue();
      assertThat(i.getResolutionType()).isNull();
      assertThat(i.isFinePayable()).isTrue();
   }

   @Test
   @DisplayName("una multa desestimada no se puede pagar")
   void dismissed() {
      Infraction i = withFine("50.00");
      i.dismiss(1L, "Error de registro");
      assertThat(i.isFinePayable()).isFalse();
   }
}
