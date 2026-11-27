package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.infraction.repository;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.infraction.entity.InfractionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface InfractionRepository extends JpaRepository<InfractionEntity, Long> {

   /**
    * Búsqueda con filtros opcionales.
    * - status/resolution: estado guardado y tipo de resolución (null = cualquiera)
    * - overdue: solo pendientes con saldo y fecha límite pasada
    * - term: parte de la placa sin guion o del código
    */
   @Query("""
         SELECT i FROM InfractionEntity i
         WHERE (:status IS NULL OR i.status = :status)
           AND (:resolution IS NULL OR i.resolutionType = :resolution)
           AND (:overdue = false OR (i.status = 'PENDING' AND i.fineAmount IS NOT NULL
                AND i.finePaid = false AND i.fineDueDate < :today))
           AND (:parkingId IS NULL OR i.parkingId = :parkingId)
           AND (:type IS NULL OR i.infractionType = :type)
           AND (:term IS NULL OR REPLACE(i.plateNumber, '-', '') LIKE CONCAT('%', :term, '%')
                OR REPLACE(i.infractionCode, '-', '') LIKE CONCAT('%', :term, '%'))
           AND (:fromTime IS NULL OR i.detectedAt >= :fromTime)
           AND (:toTime IS NULL OR i.detectedAt < :toTime)
         ORDER BY i.detectedAt DESC, i.id DESC
         """)
   Page<InfractionEntity> search(@Param("status") String status,
                                 @Param("resolution") String resolution,
                                 @Param("overdue") boolean overdue,
                                 @Param("today") LocalDate today,
                                 @Param("parkingId") Long parkingId,
                                 @Param("type") String type,
                                 @Param("term") String term,
                                 @Param("fromTime") LocalDateTime fromTime,
                                 @Param("toTime") LocalDateTime toTime,
                                 Pageable pageable);

   List<InfractionEntity> findByTransactionIdOrderByDetectedAtDesc(Long transactionId);

   Page<InfractionEntity> findByVehicleIdOrderByDetectedAtDesc(Long vehicleId, Pageable pageable);

   /** [pendientes, vencidas, pagadas, anuladas, amonestaciones] */
   @Query("""
         SELECT
           SUM(CASE WHEN i.status = 'PENDING' THEN 1 ELSE 0 END),
           SUM(CASE WHEN i.status = 'PENDING' AND i.fineAmount IS NOT NULL AND i.finePaid = false
                    AND i.fineDueDate < :today THEN 1 ELSE 0 END),
           SUM(CASE WHEN i.resolutionType = 'PAID' THEN 1 ELSE 0 END),
           SUM(CASE WHEN i.resolutionType = 'DISMISSED' THEN 1 ELSE 0 END),
           SUM(CASE WHEN i.resolutionType = 'WARNING' THEN 1 ELSE 0 END)
         FROM InfractionEntity i
         WHERE (:parkingId IS NULL OR i.parkingId = :parkingId)
         """)
   List<Object[]> countByState(@Param("parkingId") Long parkingId, @Param("today") LocalDate today);

   /** Multa total y lo pagado de las pendientes con multa (la resta se hace en Java) */
   @Query("""
         SELECT COALESCE(SUM(i.fineAmount), 0), COALESCE(SUM(i.finePaidAmount), 0)
         FROM InfractionEntity i
         WHERE i.status = 'PENDING' AND i.fineAmount IS NOT NULL
           AND (:parkingId IS NULL OR i.parkingId = :parkingId)
         """)
   List<Object[]> pendingFineTotals(@Param("parkingId") Long parkingId);

   Page<InfractionEntity> findByCustomerIdOrderByDetectedAtDesc(Long customerId, Pageable pageable);

   /** [cantidad, multas pendientes, pagado de las pendientes] de un cliente o de un vehículo */
   @Query("""
         SELECT COUNT(i),
                COALESCE(SUM(CASE WHEN i.status = 'PENDING' THEN i.fineAmount END), 0),
                COALESCE(SUM(CASE WHEN i.status = 'PENDING' THEN i.finePaidAmount END), 0)
         FROM InfractionEntity i
         WHERE (:customerId IS NULL OR i.customerId = :customerId)
           AND (:vehicleId IS NULL OR i.vehicleId = :vehicleId)
         """)
   List<Object[]> historyTotals(@Param("customerId") Long customerId, @Param("vehicleId") Long vehicleId);
}
