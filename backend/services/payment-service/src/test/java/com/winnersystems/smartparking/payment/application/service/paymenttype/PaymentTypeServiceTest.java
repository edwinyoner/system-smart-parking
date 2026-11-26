package com.winnersystems.smartparking.payment.application.service.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.command.CreatePaymentTypeCommand;
import com.winnersystems.smartparking.payment.application.dto.command.UpdatePaymentTypeCommand;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentTypeDto;
import com.winnersystems.smartparking.payment.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.payment.application.port.output.PaymentPersistencePort;
import com.winnersystems.smartparking.payment.application.port.output.PaymentTypePersistencePort;
import com.winnersystems.smartparking.payment.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.payment.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.payment.domain.model.ConfirmationMode;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.domain.model.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentTypeServiceTest {

   private static final Long USER = 20L;

   private PaymentTypePersistencePort port;
   private PaymentTypeService service;
   private PaymentPersistencePort payments;

   @BeforeEach
   void setUp() {
      port = mock(PaymentTypePersistencePort.class);
      CurrentUserPort currentUser = mock(CurrentUserPort.class);
      when(currentUser.requireCurrentUserId()).thenReturn(USER);
      when(port.findByCodeIncludingDeleted(any())).thenReturn(Optional.empty());
      when(port.save(any())).thenAnswer(inv -> {
         PaymentType t = inv.getArgument(0);
         if (t.getId() == null) t.setId(1L);
         return t;
      });
      payments = mock(PaymentPersistencePort.class);
      service = new PaymentTypeService(port, currentUser, payments);
   }

   private static PaymentType existing(Long id) {
      PaymentType t = PaymentType.create("QR", "QR", null, ConfirmationMode.AUTOMATIC, false,
            Set.of(PaymentChannel.MOBILE_APP), true, 1L);
      t.setId(id);
      return t;
   }

   @Test
   @DisplayName("crear: activo por defecto, código en mayúsculas y auditoría")
   void create() {
      PaymentTypeDto dto = service.createPaymentType(new CreatePaymentTypeCommand("cash", "Efectivo", null,
            ConfirmationMode.MANUAL, true, Set.of(PaymentChannel.OPERATOR), null));
      assertThat(dto.code()).isEqualTo("CASH");
      assertThat(dto.status()).isTrue();
      assertThat(dto.requiresReference()).isTrue();
      assertThat(dto.createdBy()).isEqualTo(USER);
   }

   @Test
   @DisplayName("código repetido: 409, y si es de uno eliminado sugiere restaurarlo")
   void duplicated() {
      PaymentType deleted = existing(2L);
      deleted.markAsDeleted(1L);
      when(port.findByCodeIncludingDeleted("QR")).thenReturn(Optional.of(deleted));
      assertThatThrownBy(() -> service.createPaymentType(new CreatePaymentTypeCommand("qr", "QR", null,
            ConfirmationMode.AUTOMATIC, false, Set.of(PaymentChannel.MOBILE_APP), true)))
            .isInstanceOf(ResourceConflictException.class)
            .hasMessageContaining("Restáuralo");
   }

   @Test
   @DisplayName("actualizar cambia canales y estado; el código no cambia")
   void update() {
      when(port.findById(3L)).thenReturn(Optional.of(existing(3L)));
      PaymentTypeDto dto = service.updatePaymentType(3L, new UpdatePaymentTypeCommand("QR nuevo", null,
            ConfirmationMode.AUTOMATIC, false, Set.of(PaymentChannel.STREET_KIOSK, PaymentChannel.EXIT_SCREEN), false));
      assertThat(dto.code()).isEqualTo("QR");
      assertThat(dto.channels()).containsExactlyInAnyOrder(PaymentChannel.STREET_KIOSK, PaymentChannel.EXIT_SCREEN);
      assertThat(dto.status()).isFalse();
      assertThat(dto.updatedBy()).isEqualTo(USER);
   }

   @Test
   @DisplayName("eliminar es lógico; restaurar lo devuelve activo; uno no eliminado da 404")
   void deleteAndRestore() {
      PaymentType type = existing(3L);
      when(port.findById(3L)).thenReturn(Optional.of(type));
      service.deletePaymentType(3L);
      assertThat(type.isDeleted()).isTrue();
      assertThat(type.getDeletedBy()).isEqualTo(USER);

      when(port.findDeletedById(3L)).thenReturn(Optional.of(type));
      assertThat(service.restorePaymentType(3L).status()).isTrue();
      when(port.findDeletedById(4L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.restorePaymentType(4L)).isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   @DisplayName("no encontrado: 404")
   void notFound() {
      when(port.findById(9L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.getPaymentTypeById(9L)).isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   @DisplayName("un tipo con pagos no se elimina (409); sí se informa cuántos tiene")
   void inUse() {
      PaymentType type = existing(3L);
      when(port.findById(3L)).thenReturn(Optional.of(type));
      when(payments.countByPaymentTypeId(3L)).thenReturn(4L);
      assertThatThrownBy(() -> service.deletePaymentType(3L))
            .isInstanceOf(ResourceConflictException.class)
            .hasMessageContaining("4 pago(s)");
      assertThat(type.isDeleted()).isFalse();
      assertThat(service.getPaymentTypeById(3L).paymentsCount()).isEqualTo(4);
   }
}
