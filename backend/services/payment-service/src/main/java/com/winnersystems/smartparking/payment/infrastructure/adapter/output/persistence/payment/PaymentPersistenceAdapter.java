package com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.payment;

import com.winnersystems.smartparking.payment.application.dto.query.PageRequest;
import com.winnersystems.smartparking.payment.application.dto.query.PageResult;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentSearchCriteria;
import com.winnersystems.smartparking.payment.application.port.output.PaymentPersistencePort;
import com.winnersystems.smartparking.payment.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.payment.domain.model.Payment;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;
import com.winnersystems.smartparking.payment.domain.model.PaymentStatus;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.payment.entity.PaymentEntity;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.payment.mapper.PaymentPersistenceMapper;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentPersistenceAdapter implements PaymentPersistencePort {

   /** Estados que parking-service debe conocer: pagado o devuelto */
   private static final EnumSet<PaymentStatus> SETTLEMENT_STATUSES =
         EnumSet.of(PaymentStatus.COMPLETED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);

   private final PaymentRepository repository;
   private final PaymentPersistenceMapper mapper;

   @Override
   public Payment save(Payment payment) {
      PaymentEntity entity = payment.getId() == null
            ? new PaymentEntity()
            : repository.findById(payment.getId())
                  .orElseThrow(() -> new ResourceNotFoundException("Pago", payment.getId()));
      mapper.copyToEntity(payment, entity);
      return mapper.toDomain(repository.save(entity));
   }

   @Override
   public Optional<Payment> findById(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Payment> findByGatewayReference(String gatewayReference) {
      return gatewayReference == null || gatewayReference.isBlank()
            ? Optional.empty()
            : repository.findFirstByGatewayReference(gatewayReference.trim()).map(mapper::toDomain);
   }

   @Override
   public List<Payment> findBySource(PaymentConcept concept, Long sourceId) {
      return repository.findByConceptAndSourceIdOrderByIdDesc(concept, sourceId).stream().map(mapper::toDomain).toList();
   }

   @Override
   public List<Payment> findExpiredPending(LocalDateTime now) {
      return repository.findByStatusAndExpiresAtLessThanEqual(PaymentStatus.PENDING, now).stream()
            .map(mapper::toDomain).toList();
   }

   @Override
   public List<Payment> findPendingSettlementNotices() {
      return repository.findBySettlementNotifiedFalseAndStatusIn(SETTLEMENT_STATUSES).stream()
            .map(mapper::toDomain).toList();
   }

   @Override
   public long countByPaymentTypeId(Long paymentTypeId) {
      return repository.countByPaymentTypeId(paymentTypeId);
   }

   @Override
   public PageResult<Payment> search(PaymentSearchCriteria c, PageRequest req) {
      String text = c.search() == null || c.search().isBlank() ? null : c.search().trim().toLowerCase();
      // %, _, [ y \ escritos por el usuario se buscan como texto (en SQL Server, [ también es comodín)
      if (text != null) {
         text = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
      }
      Sort sort = req.hasSorting()
            ? Sort.by(Sort.Direction.fromString(req.sortDirection()), req.sortBy())
            : Sort.by(Sort.Direction.DESC, "createdAt");
      Page<PaymentEntity> page = repository.search(c.status(), c.concept(), c.paymentTypeId(), c.from(), c.to(), text,
            org.springframework.data.domain.PageRequest.of(req.page(), req.size(), sort));
      return PageResult.of(page.getContent().stream().map(mapper::toDomain).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements());
   }

   /** Estados que cuentan como cobrados en los reportes */
   private static final java.util.Set<PaymentStatus> PAID_STATUSES =
         java.util.EnumSet.of(PaymentStatus.COMPLETED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);

   @Override
   public List<Payment> findPaidBetween(LocalDateTime from, LocalDateTime to, Long parkingId) {
      return repository.findPaidBetween(PAID_STATUSES, from, to, parkingId).stream().map(mapper::toDomain).toList();
   }
}
