package com.winnersystems.smartparking.payment.domain.model;

import com.winnersystems.smartparking.payment.domain.exception.PaymentDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTypeTest {

   private static PaymentType qr() {
      return PaymentType.create("qr", "Pago con QR", null, ConfirmationMode.AUTOMATIC, true,
            EnumSet.allOf(PaymentChannel.class), true, 1L);
   }

   @Test
   @DisplayName("crear: código en mayúsculas; un automático no pide número de operación")
   void create() {
      PaymentType type = qr();
      assertThat(type.getCode()).isEqualTo("QR");
      assertThat(type.isAutomatic()).isTrue();
      assertThat(type.isRequiresReference()).isFalse();
      assertThat(type.getChannels()).containsExactlyInAnyOrder(PaymentChannel.values());
      assertThat(type.getCreatedBy()).isEqualTo(1L);
   }

   @Test
   @DisplayName("un manual solo se ofrece con operador")
   void manualOnlyOperator() {
      PaymentType cash = PaymentType.create("CASH", "Efectivo", null, ConfirmationMode.MANUAL, true,
            Set.of(PaymentChannel.OPERATOR), true, 1L);
      assertThat(cash.isRequiresReference()).isTrue();
      assertThatThrownBy(() -> PaymentType.create("CASH", "Efectivo", null, ConfirmationMode.MANUAL, false,
            Set.of(PaymentChannel.OPERATOR, PaymentChannel.STREET_KIOSK), true, 1L))
            .isInstanceOf(PaymentDomainException.class)
            .hasMessageContaining("solo se ofrece con operador");
   }

   @Test
   @DisplayName("exige canal, modo, nombre y un código válido")
   void validation() {
      assertThatThrownBy(() -> PaymentType.create("QR", "QR", null, ConfirmationMode.AUTOMATIC, false, Set.of(), true, 1L))
            .isInstanceOf(PaymentDomainException.class);
      assertThatThrownBy(() -> PaymentType.create("QR", "QR", null, null, false, Set.of(PaymentChannel.OPERATOR), true, 1L))
            .isInstanceOf(PaymentDomainException.class);
      assertThatThrownBy(() -> PaymentType.create("QR", " ", null, ConfirmationMode.AUTOMATIC, false, Set.of(PaymentChannel.OPERATOR), true, 1L))
            .isInstanceOf(PaymentDomainException.class);
      assertThatThrownBy(() -> PaymentType.create("Q R", "QR", null, ConfirmationMode.AUTOMATIC, false, Set.of(PaymentChannel.OPERATOR), true, 1L))
            .isInstanceOf(PaymentDomainException.class);
   }

   @Test
   @DisplayName("se ofrece en un canal solo si está activo y lo tiene")
   void offeredIn() {
      PaymentType type = PaymentType.create("QR", "QR", null, ConfirmationMode.AUTOMATIC, false,
            Set.of(PaymentChannel.MOBILE_APP), true, 1L);
      assertThat(type.isOfferedIn(PaymentChannel.MOBILE_APP)).isTrue();
      assertThat(type.isOfferedIn(PaymentChannel.OPERATOR)).isFalse();
      type.deactivate();
      assertThat(type.isOfferedIn(PaymentChannel.MOBILE_APP)).isFalse();
   }

   @Test
   @DisplayName("eliminar lo desactiva; restaurar lo devuelve activo")
   void deleteAndRestore() {
      PaymentType type = qr();
      type.markAsDeleted(5L);
      assertThat(type.isUsable()).isFalse();
      type.restore(6L);
      assertThat(type.isActive()).isTrue();
      assertThat(type.getUpdatedBy()).isEqualTo(6L);
      assertThatThrownBy(() -> type.restore(6L)).isInstanceOf(PaymentDomainException.class);
   }

   @Test
   @DisplayName("igualdad por ID")
   void equality() {
      assertThat(qr()).isNotEqualTo(qr());
      PaymentType a = qr();
      PaymentType b = qr();
      a.setId(1L);
      b.setId(1L);
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
   }
}
