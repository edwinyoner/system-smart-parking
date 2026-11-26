package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.PaymentRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Se paga antes de salir; tras pagar hay 15 minutos para salir sin que suba el monto */
class TransactionPaymentTest {

   private static final int WINDOW = 15;
   private static final LocalDateTime ENTRY = LocalDateTime.of(2026, 11, 25, 8, 0);

   /** S/ 2.50 por hora, 5 min de gracia y 5 de tolerancia */
   private final Rate rate = Rate.create("Estándar", null, new BigDecimal("2.50"),
         new Rate.BillingRules(60, 0, 5, 5), true, 1L);

   private Transaction t;

   @BeforeEach
   void setUp() {
      t = Transaction.registerEntry(new Transaction.EntryData(1L, 2L, 3L, 4L, 5L, 6L, 7L, "12345678",
            Transaction.METHOD_MANUAL, 9L, null, null, null), ENTRY);
      t.setId(15L);
      t.setEntryTime(ENTRY);
   }

   @Test
   @DisplayName("sin pagar no sale: 409 con lo que falta y la transacción sigue activa")
   void exitRequiresPayment() {
      LocalDateTime now = ENTRY.plusMinutes(30);
      assertThat(t.amountDueAt(now, rate, WINDOW)).isEqualByComparingTo("2.50");
      assertThatThrownBy(() -> t.completeExit(Transaction.ExitData.manual(7L, "12345678", 9L), rate, now, WINDOW, false))
            .isInstanceOf(PaymentRequiredException.class)
            .hasMessageContaining("2.50");
      assertThat(t.isActive()).isTrue();
      assertThat(t.getExitTime()).isNull();
   }

   @Test
   @DisplayName("dentro del tiempo de gracia sale sin pagar (NOT_REQUIRED)")
   void graceExit() {
      t.completeExit(Transaction.ExitData.manual(7L, "12345678", 9L), rate, ENTRY.plusMinutes(3), WINDOW, false);
      assertThat(t.isCompleted()).isTrue();
      assertThat(t.getTotalAmount()).isEqualByComparingTo("0");
      assertThat(t.getPaymentStatus()).isEqualTo(Transaction.PAYMENT_STATUS_NOT_REQUIRED);
   }

   @Test
   @DisplayName("pagó y sale dentro de la ventana: se cobra hasta la hora del pago")
   void paidThenExitInWindow() {
      LocalDateTime paidAt = ENTRY.plusMinutes(58);
      t.applySettlement(new BigDecimal("2.50"), paidAt, rate);
      assertThat(t.getPaymentStatus()).isEqualTo(Transaction.PAYMENT_STATUS_PAID);

      // A las 08:70 ya serían 2 horas, pero está dentro de los 15 minutos tras pagar
      LocalDateTime exit = paidAt.plusMinutes(12);
      assertThat(t.amountDueAt(exit, rate, WINDOW)).isEqualByComparingTo("0");
      t.completeExit(Transaction.ExitData.manual(7L, "12345678", 9L), rate, exit, WINDOW, false);
      assertThat(t.getTotalAmount()).isEqualByComparingTo("2.50");
      assertThat(t.getPaymentStatus()).isEqualTo(Transaction.PAYMENT_STATUS_PAID);
      assertThat(t.getDurationMinutes()).isEqualTo(70);
   }

   @Test
   @DisplayName("se pasó de la ventana: debe la diferencia; con el segundo pago sale")
   void paidButLate() {
      LocalDateTime paidAt = ENTRY.plusMinutes(58);
      t.applySettlement(new BigDecimal("2.50"), paidAt, rate);
      LocalDateTime late = paidAt.plusMinutes(22);            // 80 minutos: 2 horas
      assertThat(t.amountDueAt(late, rate, WINDOW)).isEqualByComparingTo("2.50");
      assertThatThrownBy(() -> t.completeExit(Transaction.ExitData.manual(7L, "12345678", 9L), rate, late, WINDOW, false))
            .isInstanceOf(PaymentRequiredException.class);

      t.applySettlement(new BigDecimal("5.00"), late, rate);
      t.completeExit(Transaction.ExitData.manual(7L, "12345678", 9L), rate, late.plusMinutes(2), WINDOW, false);
      assertThat(t.getTotalAmount()).isEqualByComparingTo("5.00");
   }

   @Test
   @DisplayName("pago parcial: sigue PENDING; devolución después de salir: REFUNDED")
   void partialAndRefund() {
      t.applySettlement(new BigDecimal("1.00"), ENTRY.plusMinutes(30), rate);
      assertThat(t.getPaymentStatus()).isEqualTo(Transaction.PAYMENT_STATUS_PENDING);
      assertThat(t.amountDueAt(ENTRY.plusMinutes(31), rate, WINDOW)).isEqualByComparingTo("1.50");

      t.applySettlement(new BigDecimal("2.50"), ENTRY.plusMinutes(32), rate);
      t.completeExit(Transaction.ExitData.manual(7L, "12345678", 9L), rate, ENTRY.plusMinutes(35), WINDOW, false);
      t.applySettlement(BigDecimal.ZERO, null, rate);
      assertThat(t.getPaymentStatus()).isEqualTo(Transaction.PAYMENT_STATUS_REFUNDED);
      assertThat(t.getPaidAt()).isNull();
   }
}
