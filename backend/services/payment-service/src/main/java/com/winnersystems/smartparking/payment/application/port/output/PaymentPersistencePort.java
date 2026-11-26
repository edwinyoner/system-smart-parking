package com.winnersystems.smartparking.payment.application.port.output;

import com.winnersystems.smartparking.payment.application.dto.query.PageRequest;
import com.winnersystems.smartparking.payment.application.dto.query.PageResult;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentSearchCriteria;
import com.winnersystems.smartparking.payment.domain.model.Payment;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentPersistencePort {

   Payment save(Payment payment);

   Optional<Payment> findById(Long id);

   Optional<Payment> findByGatewayReference(String gatewayReference);

   /** Todos los pagos de una estadía o una multa, el más reciente primero */
   List<Payment> findBySource(PaymentConcept concept, Long sourceId);

   /** QR pendientes cuyo vencimiento ya pasó */
   List<Payment> findExpiredPending(LocalDateTime now);

   /** Pagos o devoluciones que parking-service todavía no registró */
   List<Payment> findPendingSettlementNotices();

   /** Pagos hechos con un tipo de pago (para no eliminar un tipo en uso) */
   long countByPaymentTypeId(Long paymentTypeId);

   PageResult<Payment> search(PaymentSearchCriteria criteria, PageRequest pageRequest);
}
