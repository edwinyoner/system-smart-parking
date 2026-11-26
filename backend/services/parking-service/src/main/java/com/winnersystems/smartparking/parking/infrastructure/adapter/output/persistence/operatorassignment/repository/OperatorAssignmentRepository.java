package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.operatorassignment.repository;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.operatorassignment.entity.OperatorAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Rangos de fechas inclusivos; un fin nulo es indefinido (se compara con :end = 9999-12-31).
 */
public interface OperatorAssignmentRepository extends JpaRepository<OperatorAssignmentEntity, Long> {

   @Query("""
         SELECT a FROM OperatorAssignmentEntity a
         WHERE (:parkingId IS NULL OR a.parkingId = :parkingId)
           AND (:zoneId IS NULL OR a.zoneId = :zoneId)
           AND (:operatorId IS NULL OR a.operatorId = :operatorId)
         ORDER BY a.startDate DESC, a.id DESC
         """)
   List<OperatorAssignmentEntity> search(@Param("parkingId") Long parkingId,
                                         @Param("zoneId") Long zoneId,
                                         @Param("operatorId") Long operatorId);

   @Query("""
         SELECT a FROM OperatorAssignmentEntity a
         WHERE a.operatorId = :operatorId
           AND a.startDate <= :end AND (a.endDate IS NULL OR a.endDate >= :start)
         """)
   List<OperatorAssignmentEntity> findByOperatorOverlapping(@Param("operatorId") Long operatorId,
                                                            @Param("start") LocalDate start,
                                                            @Param("end") LocalDate end);

   @Query("""
         SELECT a FROM OperatorAssignmentEntity a
         WHERE a.parkingId = :parkingId AND a.shiftId = :shiftId
           AND a.startDate <= :end AND (a.endDate IS NULL OR a.endDate >= :start)
         """)
   List<OperatorAssignmentEntity> findByParkingShiftOverlapping(@Param("parkingId") Long parkingId,
                                                                @Param("shiftId") Long shiftId,
                                                                @Param("start") LocalDate start,
                                                                @Param("end") LocalDate end);

   @Query("""
         SELECT COUNT(a) FROM OperatorAssignmentEntity a
         WHERE a.parkingId = :parkingId AND a.shiftId = :shiftId
           AND (a.endDate IS NULL OR a.endDate >= :today)
         """)
   long countCurrentOrFuture(@Param("parkingId") Long parkingId,
                             @Param("shiftId") Long shiftId,
                             @Param("today") LocalDate today);
}
