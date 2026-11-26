package com.winnersystems.smartparking.payment.application.service.payment;

import com.winnersystems.smartparking.payment.application.dto.command.CreatePaymentCommand;
import com.winnersystems.smartparking.payment.application.dto.command.GatewayNotification;
import com.winnersystems.smartparking.payment.application.dto.command.RefundPaymentCommand;
import com.winnersystems.smartparking.payment.application.dto.query.*;
import com.winnersystems.smartparking.payment.application.port.input.payment.*;
import com.winnersystems.smartparking.payment.application.port.output.ChargePort;
import com.winnersystems.smartparking.payment.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.payment.application.port.output.PaymentGatewayPort;
import com.winnersystems.smartparking.payment.application.port.output.PaymentPersistencePort;
import com.winnersystems.smartparking.payment.application.port.output.PaymentTypePersistencePort;
import com.winnersystems.smartparking.payment.domain.exception.PaymentDomainException;
import com.winnersystems.smartparking.payment.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.payment.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.payment.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Casos de uso de los pagos.
 *
 * Flujo:
 * 1. Se pregunta a parking-service cuánto se debe (el cliente nunca envía el monto). Ya descuenta
 *    lo pagado: una estadía puede tener un segundo pago por la diferencia.
 * 2. Efectivo: el pago nace COMPLETED. QR: nace PENDING y se crea el cobro en la pasarela.
 * 3. Con el pago COMPLETED se avisa a parking-service, que marca la estadía o la multa como pagada.
 *
 * Los métodos que avisan a parking-service no son una sola transacción a propósito: primero se
 * guarda el pago (queda firme aunque parking-service no responda) y luego se avisa. Si el aviso
 * falla, settlementNotified queda en false y la tarea periódica lo reintenta.
 */
@Slf4j
@Service
public class PaymentService implements
      CreatePaymentUseCase,
      GetPaymentUseCase,
      ListPaymentsUseCase,
      CancelPaymentUseCase,
      RefundPaymentUseCase,
      HandleGatewayNotificationUseCase,
      PaymentMaintenanceUseCase {

   private static final String RESOURCE = "Pago";

   private final PaymentPersistencePort paymentPort;
   private final PaymentTypePersistencePort paymentTypePort;
   private final ChargePort chargePort;
   private final PaymentGatewayPort gatewayPort;
   private final CurrentUserPort currentUserPort;
   private final Clock clock;
   private final int qrExpirationMinutes;

   public PaymentService(
         PaymentPersistencePort paymentPort,
         PaymentTypePersistencePort paymentTypePort,
         ChargePort chargePort,
         PaymentGatewayPort gatewayPort,
         CurrentUserPort currentUserPort,
         Clock clock,
         @Value("${payment.qr.expiration-minutes:10}") int qrExpirationMinutes) {
      this.paymentPort = paymentPort;
      this.paymentTypePort = paymentTypePort;
      this.chargePort = chargePort;
      this.gatewayPort = gatewayPort;
      this.currentUserPort = currentUserPort;
      this.clock = clock;
      this.qrExpirationMinutes = qrExpirationMinutes;
   }

   // ========================= CREAR =========================

   @Override
   public PaymentDto createPayment(CreatePaymentCommand command) {
      Long userId = currentUserPort.requireCurrentUserId();
      if (command.concept() == null || command.sourceId() == null) {
         throw new PaymentDomainException("Indica qué se paga: la estadía o la multa y su ID");
      }

      PaymentType type = paymentTypePort.findById(command.paymentTypeId())
            .orElseThrow(() -> new ResourceNotFoundException("Tipo de pago", command.paymentTypeId()));
      if (!type.isActive()) {
         throw new PaymentDomainException("El tipo de pago " + type.getName() + " está desactivado");
      }
      PaymentChannel channel = type.isAutomatic()
            ? requireChannel(command.channel())
            : PaymentChannel.OPERATOR;   // un pago manual siempre lo registra un operador

      // 1. Cuánto se debe, según parking-service
      ChargeQuote quote = chargePort.getCharge(command.concept(), command.sourceId());
      if (!quote.payable()) {
         throw new ResourceConflictException(quote.reason() != null ? quote.reason() : "No hay nada por pagar");
      }
      Payment.Charge charge = new Payment.Charge(command.concept(), command.sourceId(),
            quote.amountDue(), quote.description(), quote.plateNumber());

      // 2. Si ya se pagó todo, parking-service responde payable = false (arriba). Si aún se debe
      //    (por ejemplo, la diferencia por pasarse del tiempo para salir), se cobra lo que falta.
      //    Un QR pendiente se reutiliza o se reemplaza.
      List<Payment> previous = paymentPort.findBySource(command.concept(), command.sourceId());
      LocalDateTime now = LocalDateTime.now(clock);
      for (Payment pending : previous.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).toList()) {
         if (canReuse(pending, type, channel, charge, now)) {
            log.info("Se reutiliza el QR pendiente: pago={}", pending.getId());
            return toDto(pending);
         }
         cancelPending(pending, "Reemplazado por otro intento de pago", userId);
      }

      // 3. Efectivo: cobrado. QR: pendiente con su cobro en la pasarela
      if (!type.isAutomatic()) {
         Payment saved = paymentPort.save(Payment.manual(charge, type, command.operationNumber(), userId));
         log.info("Pago manual registrado: id={}, {}#{}, monto={}, operador={}",
               saved.getId(), saved.getConcept(), saved.getSourceId(), saved.getAmount(), userId);
         return toDto(notifyParking(saved));
      }

      Payment payment = paymentPort.save(Payment.automatic(charge, type, channel, userId));
      LocalDateTime expiresAt = now.plusMinutes(qrExpirationMinutes);
      GatewayCharge gatewayCharge = gatewayPort.createQrCharge(
            payment.getId(), payment.getAmount(), payment.getDescription(), expiresAt);
      payment.attachGatewayCharge(gatewayCharge.gatewayReference(), gatewayCharge.qrPayload(), gatewayCharge.expiresAt());
      Payment saved = paymentPort.save(payment);
      log.info("QR generado: pago={}, referencia={}, vence={}", saved.getId(), saved.getGatewayReference(), saved.getExpiresAt());
      return toDto(saved);
   }

   /** Mismo tipo, canal y monto, y el QR sigue vigente */
   private static boolean canReuse(Payment pending, PaymentType type, PaymentChannel channel,
                                   Payment.Charge charge, LocalDateTime now) {
      return pending.getPaymentTypeId().equals(type.getId())
            && pending.getChannel() == channel
            && pending.getAmount().compareTo(charge.amount().setScale(2, java.math.RoundingMode.HALF_UP)) == 0
            && pending.getGatewayReference() != null
            && !pending.isExpiredAt(now);
   }

   private static PaymentChannel requireChannel(PaymentChannel channel) {
      if (channel == null) {
         throw new PaymentDomainException("Indica el canal del pago (pantalla, kiosco, app u operador)");
      }
      return channel;
   }

   // ========================= PASARELA =========================

   @Override
   public void handleGatewayNotification(GatewayNotification notification) {
      Payment payment = paymentPort.findByGatewayReference(notification.gatewayReference())
            .orElseThrow(() -> new ResourceNotFoundException(
                  "No hay un pago con la referencia " + notification.gatewayReference()));

      // Un aviso tardío (pago ya cancelado o vencido) no cambia nada: la pasarela no debió aceptarlo
      if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.COMPLETED
            && payment.getStatus() != PaymentStatus.FAILED) {
         log.warn("Aviso de la pasarela ignorado: pago={} está {}", payment.getId(), payment.getStatus());
         return;
      }

      if (notification.succeeded()) {
         if (payment.getStatus() == PaymentStatus.FAILED) {
            log.warn("Aviso de pago para un cobro ya rechazado: pago={}", payment.getId());
            return;
         }
         if (payment.confirm(notification.operationNumber(), notification.paidAt())) {
            Payment saved = paymentPort.save(payment);
            log.info("Pago confirmado por la pasarela: id={}, operación={}", saved.getId(), saved.getOperationNumber());
            notifyParking(saved);
         } else {
            log.debug("Aviso repetido de la pasarela: pago={}", payment.getId());
         }
      } else if (payment.getStatus() == PaymentStatus.PENDING) {
         payment.fail(notification.failureReason());
         paymentPort.save(payment);
         log.info("Pago rechazado por la pasarela: id={}, motivo={}", payment.getId(), notification.failureReason());
      }
   }

   // ========================= CANCELAR Y DEVOLVER =========================

   @Override
   public PaymentDto cancelPayment(Long paymentId, String reason) {
      Payment payment = findOrThrow(paymentId);
      cancelPending(payment, reason, currentUserPort.requireCurrentUserId());
      return toDto(payment);
   }

   private void cancelPending(Payment payment, String reason, Long userId) {
      payment.cancel(reason, userId);
      paymentPort.save(payment);
      if (payment.getGatewayReference() != null) {
         gatewayPort.cancelCharge(payment.getGatewayReference());
      }
      log.info("Pago cancelado: id={}, motivo={}", payment.getId(), reason);
   }

   @Override
   public PaymentDto refundPayment(Long paymentId, RefundPaymentCommand command) {
      Payment payment = findOrThrow(paymentId);
      boolean full = payment.refund(command.amount(), command.reason(), currentUserPort.requireCurrentUserId());
      Payment saved = paymentPort.save(payment);
      log.info("Devolución registrada: pago={}, monto={}, total={}", saved.getId(), command.amount(), full);
      return toDto(notifyParking(saved));
   }

   // ========================= CONSULTAS =========================

   @Override
   @Transactional(readOnly = true)
   public PaymentDto getPaymentById(Long paymentId) {
      return toDto(findOrThrow(paymentId));
   }

   @Override
   @Transactional(readOnly = true)
   public List<PaymentDto> listPaymentsBySource(PaymentConcept concept, Long sourceId) {
      return paymentPort.findBySource(concept, sourceId).stream().map(this::toDto).toList();
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<PaymentDto> listPayments(PaymentSearchCriteria criteria, int page, int size) {
      return paymentPort.search(criteria, PageRequest.of(page, size, "createdAt", "DESC"))
            .toPagedResponse(this::toDto);
   }

   // ========================= TAREAS PERIÓDICAS =========================

   @Override
   public int expirePendingPayments() {
      LocalDateTime now = LocalDateTime.now(clock);
      int count = 0;
      for (Payment payment : paymentPort.findExpiredPending(now)) {
         payment.expire(now);
         paymentPort.save(payment);
         if (payment.getGatewayReference() != null) {
            gatewayPort.cancelCharge(payment.getGatewayReference());
         }
         count++;
      }
      if (count > 0) {
         log.info("{} QR vencidos sin pagar", count);
      }
      return count;
   }

   @Override
   public int retrySettlementNotices() {
      int delivered = 0;
      for (Payment payment : paymentPort.findPendingSettlementNotices()) {
         if (notifyParking(payment).isSettlementNotified()) {
            delivered++;
         }
      }
      return delivered;
   }

   // ========================= AUXILIARES =========================

   /** Avisa a parking-service; si falla, queda para el reintento */
   private Payment notifyParking(Payment payment) {
      try {
         chargePort.notifySettlement(payment);
         payment.markSettlementNotified();
         return paymentPort.save(payment);
      } catch (RuntimeException e) {
         log.warn("parking-service no registró el pago {} todavía (se reintentará): {}",
               payment.getId(), e.getMessage());
         return payment;
      }
   }

   private Payment findOrThrow(Long id) {
      return paymentPort.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
   }

   private PaymentDto toDto(Payment p) {
      return new PaymentDto(
            p.getId(), p.getConcept(), p.getSourceId(), p.getDescription(), p.getPlateNumber(),
            p.getPaymentTypeId(), p.getPaymentTypeCode(), p.getPaymentTypeName(), p.getConfirmationMode(),
            p.getChannel(), p.getAmount(), p.getCurrency(), p.getStatus(),
            p.getGatewayReference(), p.getQrPayload(), p.getExpiresAt(), p.getOperationNumber(),
            p.getOperatorId(), p.getPaidAt(), p.getFailureReason(), p.getCancelReason(),
            p.getRefundedAmount(), p.getRefundableAmount(), p.getRefundReason(), p.getRefundedAt(), p.getRefundedBy(),
            p.isSettlementNotified(), p.getCreatedAt(), p.getCreatedBy(), p.getUpdatedAt(), p.getUpdatedBy());
   }
}
