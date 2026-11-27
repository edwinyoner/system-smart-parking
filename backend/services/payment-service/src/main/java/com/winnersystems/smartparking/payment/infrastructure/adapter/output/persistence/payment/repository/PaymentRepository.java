package com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.payment.repository;

import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;
import com.winnersystems.smartparking.payment.domain.model.PaymentStatus;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.payment.entity.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

   Optional<PaymentEntity> findFirstByGatewayReference(String gatewayReference);

   List<PaymentEntity> findByConceptAndSourceIdOrderByIdDesc(PaymentConcept concept, Long sourceId);

   List<PaymentEntity> findByStatusAndExpiresAtLessThanEqual(PaymentStatus status, LocalDateTime now);

   List<PaymentEntity> findBySettlementNotifiedFalseAndStatusIn(Collection<PaymentStatus> statuses);

   long countByPaymentTypeId(Long paymentTypeId);

   /** search llega en minúsculas y con %, _, [ y \ escapados */
   @Query("""
         SELECT p FROM PaymentEntity p
         WHERE (:status IS NULL OR p.status = :status)
           AND (:concept IS NULL OR p.concept = :concept)
           AND (:paymentTypeId IS NULL OR p.paymentTypeId = :paymentTypeId)
           AND (:fromDate IS NULL OR p.createdAt >= :fromDate)
           AND (:toDate IS NULL OR p.createdAt < :toDate)
           AND (:search IS NULL
                OR LOWER(p.plateNumber) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(p.description) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(p.operationNumber) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(p.gatewayReference) LIKE CONCAT('%', :search, '%') ESCAPE '\\')
         """)
   Page<PaymentEntity> search(@Param("status") PaymentStatus status,
                              @Param("concept") PaymentConcept concept,
                              @Param("paymentTypeId") Long paymentTypeId,
                              @Param("fromDate") LocalDateTime fromDate,
                              @Param("toDate") LocalDateTime toDate,
                              @Param("search") String search,
                              Pageable pageable);

   @Query("""
         SELECT p FROM PaymentEntity p
         WHERE p.status IN :statuses
           AND p.paidAt >= :fromTime AND p.paidAt < :toTime
           AND (:parkingId IS NULL OR p.parkingId = :parkingId)
         ORDER BY p.paidAt
         """)
   List<PaymentEntity> findPaidBetween(@Param("statuses") Collection<PaymentStatus> statuses,
                                       @Param("fromTime") LocalDateTime from,
                                       @Param("toTime") LocalDateTime to,
                                       @Param("parkingId") Long parkingId);
}
