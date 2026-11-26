package com.winnersystems.smartparking.payment.domain.model;

import com.winnersystems.smartparking.payment.domain.exception.PaymentDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

   static PaymentType qr() {
      PaymentType t = PaymentType.create("QR", "Pago con QR", null, ConfirmationMode.AUTOMATIC, false,
            EnumSet.allOf(PaymentChannel.class), true, 1L);
      t.setId(1L);
      return t;
   }

   static PaymentType cash(boolean requiresReference) {
      PaymentType t = PaymentType.create("CASH", "Efectivo", null, ConfirmationMode.MANUAL, requiresReference,
            Set.of(PaymentChannel.OPERATOR), true, 1L);
      t.setId(2L);
      return t;
   }

   static Payment.Charge stay(String amount) {
      return new Payment.Charge(PaymentConcept.PARKING_STAY, 15L, new BigDecimal(amount), "Estadía ABC-123", "ABC-123");
   }

   @Test
   @DisplayName("efectivo: nace COMPLETED con el operador y copia los datos del tipo")
   void manual() {
      Payment p = Payment.manual(stay("7.5"), cash(false), null, 9L);
      assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
      assertThat(p.getAmount()).isEqualByComparingTo("7.50");
      assertThat(p.getAmount().scale()).isEqualTo(2);
      assertThat(p.getChannel()).isEqualTo(PaymentChannel.OPERATOR);
      assertThat(p.getOperatorId()).isEqualTo(9L);
      assertThat(p.getPaidAt()).isNotNull();
      assertThat(p.getPaymentTypeCode()).isEqualTo("CASH");
      assertThat(p.needsSettlementNotice()).isTrue();
   }

   @Test
   @DisplayName("efectivo con número de operación obligatorio; un QR no se registra como manual")
   void manualRules() {
      assertThatThrownBy(() -> Payment.manual(stay("5"), cash(true), " ", 9L))
            .isInstanceOf(PaymentDomainException.class).hasMessageContaining("número de operación");
      assertThat(Payment.manual(stay("5"), cash(true), "A-1", 9L).getOperationNumber()).isEqualTo("A-1");
      assertThatThrownBy(() -> Payment.manual(stay("5"), qr(), null, 9L)).isInstanceOf(PaymentDomainException.class);
      assertThatThrownBy(() -> Payment.automatic(stay("5"), cash(false), PaymentChannel.OPERATOR, 9L))
            .isInstanceOf(PaymentDomainException.class);
   }

   @Test
   @DisplayName("sin monto o en un canal que el tipo no ofrece: no se crea")
   void invalidCharge() {
      assertThatThrownBy(() -> Payment.automatic(stay("0"), qr(), PaymentChannel.MOBILE_APP, 1L))
            .isInstanceOf(PaymentDomainException.class);
      PaymentType appOnly = PaymentType.create("QRAPP", "QR app", null, ConfirmationMode.AUTOMATIC, false,
            Set.of(PaymentChannel.MOBILE_APP), true, 1L);
      assertThatThrownBy(() -> Payment.automatic(stay("5"), appOnly, PaymentChannel.STREET_KIOSK, 1L))
            .isInstanceOf(PaymentDomainException.class).hasMessageContaining("canal");
   }

   @Test
   @DisplayName("QR: PENDING, se confirma una sola vez y luego avisa a parking-service")
   void automaticConfirm() {
      Payment p = Payment.automatic(stay("5"), qr(), PaymentChannel.EXIT_SCREEN, 1L);
      assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
      assertThat(p.needsSettlementNotice()).isFalse();
      p.attachGatewayCharge("SIM-1", "qr", LocalDateTime.now().plusMinutes(10));
      assertThat(p.confirm("OP1", null)).isTrue();
      assertThat(p.confirm("OP1", null)).isFalse();
      assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
      assertThat(p.getOperationNumber()).isEqualTo("OP1");
      assertThat(p.needsSettlementNotice()).isTrue();
      p.markSettlementNotified();
      assertThat(p.needsSettlementNotice()).isFalse();
   }

   @Test
   @DisplayName("QR vencido, rechazado o cancelado: ya no se confirma")
   void closedStates() {
      LocalDateTime now = LocalDateTime.now();
      Payment expired = Payment.automatic(stay("5"), qr(), PaymentChannel.EXIT_SCREEN, 1L);
      expired.attachGatewayCharge("SIM-2", "qr", now.minusSeconds(1));
      assertThat(expired.isExpiredAt(now)).isTrue();
      expired.expire(now);
      assertThat(expired.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
      assertThatThrownBy(() -> expired.confirm("OP", null)).isInstanceOf(PaymentDomainException.class);

      Payment rejected = Payment.automatic(stay("5"), qr(), PaymentChannel.EXIT_SCREEN, 1L);
      assertThat(rejected.fail("Sin saldo")).isTrue();
      assertThat(rejected.fail("Sin saldo")).isFalse();

      Payment cancelled = Payment.automatic(stay("5"), qr(), PaymentChannel.EXIT_SCREEN, 1L);
      assertThatThrownBy(() -> cancelled.cancel(" ", 1L)).isInstanceOf(PaymentDomainException.class);
      cancelled.cancel("El cliente pagó en efectivo", 1L);
      assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
      assertThatThrownBy(() -> cancelled.cancel("otra vez", 1L)).isInstanceOf(PaymentDomainException.class);
   }

   @Test
   @DisplayName("devolución parcial y luego total, nunca más de lo pagado")
   void refunds() {
      Payment p = Payment.manual(stay("10"), cash(false), null, 9L);
      p.markSettlementNotified();
      assertThat(p.refund(new BigDecimal("4"), "Cobro de más", 3L)).isFalse();
      assertThat(p.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
      assertThat(p.getRefundableAmount()).isEqualByComparingTo("6.00");
      assertThat(p.needsSettlementNotice()).isTrue();
      assertThatThrownBy(() -> p.refund(new BigDecimal("6.01"), "x", 3L)).isInstanceOf(PaymentDomainException.class);
      assertThatThrownBy(() -> p.refund(new BigDecimal("1"), " ", 3L)).isInstanceOf(PaymentDomainException.class);
      assertThat(p.refund(new BigDecimal("6"), "Anulado", 3L)).isTrue();
      assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
      assertThat(p.getRefundableAmount()).isEqualByComparingTo("0");
      assertThatThrownBy(() -> p.refund(new BigDecimal("1"), "x", 3L)).isInstanceOf(PaymentDomainException.class);

      Payment pending = Payment.automatic(stay("5"), qr(), PaymentChannel.EXIT_SCREEN, 1L);
      assertThatThrownBy(() -> pending.refund(new BigDecimal("1"), "x", 3L)).isInstanceOf(PaymentDomainException.class);
   }
}
