package com.winnersystems.smartparking.payment.application.service.payment;

import com.winnersystems.smartparking.payment.application.dto.command.CreatePaymentCommand;
import com.winnersystems.smartparking.payment.application.dto.command.GatewayNotification;
import com.winnersystems.smartparking.payment.application.dto.command.RefundPaymentCommand;
import com.winnersystems.smartparking.payment.application.dto.query.ChargeQuote;
import com.winnersystems.smartparking.payment.application.dto.query.GatewayCharge;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentDto;
import com.winnersystems.smartparking.payment.application.port.output.*;
import com.winnersystems.smartparking.payment.domain.exception.PaymentDomainException;
import com.winnersystems.smartparking.payment.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.payment.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

   private static final Long OPERATOR = 9L;
   private static final ZoneId LIMA = ZoneId.of("America/Lima");
   private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), LIMA);
   private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

   private PaymentPersistencePort payments;
   private PaymentTypePersistencePort types;
   private ChargePort charges;
   private PaymentGatewayPort gateway;
   private PaymentService service;

   /** "Base de datos" en memoria para seguir el pago entre llamadas */
   private final Map<Long, Payment> db = new HashMap<>();

   @BeforeEach
   void setUp() {
      payments = mock(PaymentPersistencePort.class);
      types = mock(PaymentTypePersistencePort.class);
      charges = mock(ChargePort.class);
      gateway = mock(PaymentGatewayPort.class);
      CurrentUserPort currentUser = mock(CurrentUserPort.class);
      when(currentUser.requireCurrentUserId()).thenReturn(OPERATOR);

      AtomicLong ids = new AtomicLong(100);
      when(payments.save(any())).thenAnswer(inv -> {
         Payment p = inv.getArgument(0);
         if (p.getId() == null) p.setId(ids.incrementAndGet());
         db.put(p.getId(), p);
         return p;
      });
      when(payments.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<Long>getArgument(0))));
      when(payments.findBySource(any(), anyLong())).thenAnswer(inv -> db.values().stream()
            .filter(p -> p.getConcept() == inv.getArgument(0) && p.getSourceId().equals(inv.getArgument(1)))
            .sorted(Comparator.comparing(Payment::getId).reversed()).toList());
      when(payments.findByGatewayReference(any())).thenAnswer(inv -> db.values().stream()
            .filter(p -> inv.getArgument(0).equals(p.getGatewayReference())).findFirst());

      when(types.findById(1L)).thenReturn(Optional.of(type(1L, "QR", ConfirmationMode.AUTOMATIC, EnumSet.allOf(PaymentChannel.class))));
      when(types.findById(2L)).thenReturn(Optional.of(type(2L, "CASH", ConfirmationMode.MANUAL, Set.of(PaymentChannel.OPERATOR))));

      when(charges.getCharge(PaymentConcept.PARKING_STAY, 15L))
            .thenReturn(new ChargeQuote(new BigDecimal("7.50"), "Estadía ABC-123", "ABC-123", true, null));
      when(gateway.createQrCharge(anyLong(), any(), any(), any()))
            .thenAnswer(inv -> new GatewayCharge("SIM-" + inv.getArgument(0), "QR-DATA", inv.getArgument(3)));

      service = new PaymentService(payments, types, charges, gateway, currentUser, CLOCK, 10);
   }

   private static PaymentType type(Long id, String code, ConfirmationMode mode, Set<PaymentChannel> channels) {
      PaymentType t = PaymentType.create(code, code, null, mode, false, channels, true, 1L);
      t.setId(id);
      return t;
   }

   private PaymentDto payCash() {
      return service.createPayment(new CreatePaymentCommand(PaymentConcept.PARKING_STAY, 15L, 2L, null, null));
   }

   private PaymentDto payQr() {
      return service.createPayment(new CreatePaymentCommand(PaymentConcept.PARKING_STAY, 15L, 1L, PaymentChannel.EXIT_SCREEN, null));
   }

   @Test
   @DisplayName("efectivo: monto de parking-service, COMPLETED y aviso entregado")
   void cash() {
      PaymentDto dto = payCash();
      assertThat(dto.status()).isEqualTo(PaymentStatus.COMPLETED);
      assertThat(dto.amount()).isEqualByComparingTo("7.50");
      assertThat(dto.channel()).isEqualTo(PaymentChannel.OPERATOR);
      assertThat(dto.settlementNotified()).isTrue();
      verify(charges).notifySettlement(any());
      verifyNoInteractions(gateway);
   }

   @Test
   @DisplayName("si parking-service no responde, el pago queda firme y el aviso se reintenta")
   void notifyRetry() {
      doThrow(new RuntimeException("caído")).when(charges).notifySettlement(any());
      PaymentDto dto = payCash();
      assertThat(dto.status()).isEqualTo(PaymentStatus.COMPLETED);
      assertThat(dto.settlementNotified()).isFalse();

      doNothing().when(charges).notifySettlement(any());
      when(payments.findPendingSettlementNotices()).thenReturn(List.of(db.get(dto.id())));
      assertThat(service.retrySettlementNotices()).isEqualTo(1);
      assertThat(db.get(dto.id()).isSettlementNotified()).isTrue();
   }

   @Test
   @DisplayName("QR: PENDING con referencia, QR y vencimiento en 10 minutos")
   void qr() {
      PaymentDto dto = payQr();
      assertThat(dto.status()).isEqualTo(PaymentStatus.PENDING);
      assertThat(dto.gatewayReference()).isEqualTo("SIM-" + dto.id());
      assertThat(dto.qrPayload()).isEqualTo("QR-DATA");
      assertThat(dto.expiresAt()).isEqualTo(NOW.plusMinutes(10));
      verify(charges, never()).notifySettlement(any());
   }

   @Test
   @DisplayName("pedir otra vez el mismo QR lo reutiliza; cambiar a efectivo cancela el QR")
   void reuseOrReplace() {
      PaymentDto first = payQr();
      assertThat(payQr().id()).isEqualTo(first.id());
      verify(gateway, times(1)).createQrCharge(anyLong(), any(), any(), any());

      PaymentDto cash = payCash();
      assertThat(db.get(first.id()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
      verify(gateway).cancelCharge(first.gatewayReference());
      assertThat(cash.status()).isEqualTo(PaymentStatus.COMPLETED);
   }

   @Test
   @DisplayName("parking-service dice que no hay deuda: 409 con su motivo; si aún se debe, se cobra la diferencia")
   void conflicts() {
      payCash();
      when(charges.getCharge(PaymentConcept.PARKING_STAY, 15L))
            .thenReturn(new ChargeQuote(new BigDecimal("2.50"), "Estadía ABC-123", "ABC-123", true, null));
      assertThat(payCash().amount()).isEqualByComparingTo("2.50");
      when(charges.getCharge(PaymentConcept.PARKING_STAY, 15L))
            .thenReturn(new ChargeQuote(BigDecimal.ZERO, null, null, false, "La estadía ya está pagada"));
      assertThatThrownBy(this::payQr).isInstanceOf(ResourceConflictException.class).hasMessage("La estadía ya está pagada");

      when(charges.getCharge(PaymentConcept.FINE, 7L)).thenReturn(new ChargeQuote(null, null, null, false, "La multa fue anulada"));
      assertThatThrownBy(() -> service.createPayment(new CreatePaymentCommand(PaymentConcept.FINE, 7L, 2L, null, null)))
            .isInstanceOf(ResourceConflictException.class).hasMessage("La multa fue anulada");
   }

   @Test
   @DisplayName("un QR sin canal o un tipo desactivado: 400")
   void invalid() {
      assertThatThrownBy(() -> service.createPayment(new CreatePaymentCommand(PaymentConcept.PARKING_STAY, 15L, 1L, null, null)))
            .isInstanceOf(PaymentDomainException.class);
      types.findById(1L).orElseThrow().deactivate();
      assertThatThrownBy(this::payQr).isInstanceOf(PaymentDomainException.class).hasMessageContaining("desactivado");
   }

   @Test
   @DisplayName("webhook: confirma, avisa a parking-service y un aviso repetido no hace nada")
   void webhook() {
      PaymentDto dto = payQr();
      GatewayNotification ok = new GatewayNotification(dto.gatewayReference(), true, "OP77", NOW, null);
      service.handleGatewayNotification(ok);
      assertThat(db.get(dto.id()).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
      assertThat(db.get(dto.id()).getOperationNumber()).isEqualTo("OP77");
      assertThat(db.get(dto.id()).isSettlementNotified()).isTrue();

      service.handleGatewayNotification(ok);
      verify(charges, times(1)).notifySettlement(any());
   }

   @Test
   @DisplayName("webhook de rechazo: FAILED; aviso tardío de un QR cancelado: se ignora")
   void webhookRejectAndLate() {
      PaymentDto rejected = payQr();
      service.handleGatewayNotification(new GatewayNotification(rejected.gatewayReference(), false, null, null, "Sin saldo"));
      assertThat(db.get(rejected.id()).getStatus()).isEqualTo(PaymentStatus.FAILED);
      assertThat(db.get(rejected.id()).getFailureReason()).isEqualTo("Sin saldo");

      PaymentDto cancelled = payQr();
      service.cancelPayment(cancelled.id(), "El cliente se fue");
      service.handleGatewayNotification(new GatewayNotification(cancelled.gatewayReference(), true, "OP", NOW, null));
      assertThat(db.get(cancelled.id()).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
      verify(charges, never()).notifySettlement(any());
   }

   @Test
   @DisplayName("vencimiento: los QR vencidos pasan a EXPIRED y se anulan en la pasarela")
   void expire() {
      PaymentDto dto = payQr();
      Payment p = db.get(dto.id());
      p.setExpiresAt(NOW.minusMinutes(1));
      when(payments.findExpiredPending(NOW)).thenReturn(List.of(p));
      assertThat(service.expirePendingPayments()).isEqualTo(1);
      assertThat(p.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
      verify(gateway).cancelCharge(dto.gatewayReference());
   }

   @Test
   @DisplayName("devolución: registra quién y avisa a parking-service")
   void refund() {
      PaymentDto paid = payCash();
      PaymentDto refunded = service.refundPayment(paid.id(), new RefundPaymentCommand(new BigDecimal("7.50"), "Cobro duplicado"));
      assertThat(refunded.status()).isEqualTo(PaymentStatus.REFUNDED);
      assertThat(refunded.refundedBy()).isEqualTo(OPERATOR);
      verify(charges, times(2)).notifySettlement(any());
   }
}
