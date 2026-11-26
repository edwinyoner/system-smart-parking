package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.transaction.repository;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.transaction.entity.TransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repositorio Spring Data JPA para TransactionEntity.
 *
 * OPTIMIZADO PARA ORACLE DATABASE
 *
 * NOTA: Las búsquedas por placa (findByPlateNumber, searchByPlate)
 * se implementan en el SERVICE layer, NO aquí, porque requieren
 * primero buscar el vehicleId desde VehiclePersistencePort.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

   // ========================= TRANSACCIONES ACTIVAS =========================

   @Query("SELECT t FROM TransactionEntity t WHERE t.vehicleId = :vehicleId AND t.status = 'ACTIVE'")
   Optional<TransactionEntity> findActiveByVehicleId(@Param("vehicleId") Long vehicleId);

   @Query("SELECT t FROM TransactionEntity t WHERE t.status = 'ACTIVE'")
   Page<TransactionEntity> findAllActive(Pageable pageable);

   @Query("SELECT t FROM TransactionEntity t WHERE t.zoneId = :zoneId AND t.status = 'ACTIVE'")
   Page<TransactionEntity> findActiveByZoneId(@Param("zoneId") Long zoneId, Pageable pageable);

   /**
    * Lista transacciones activas que superan el tiempo máximo permitido.
    *
    * Oracle: EXTRACT(DAY/HOUR/MINUTE FROM interval) para calcular minutos transcurridos.
    */
   @Query(value = "SELECT * FROM TRANSACTIONS t " +
         "WHERE t.STATUS = 'ACTIVE' " +
         "AND EXTRACT(DAY FROM (CURRENT_TIMESTAMP - t.ENTRY_TIME)) * 24 * 60 + " +
         "    EXTRACT(HOUR FROM (CURRENT_TIMESTAMP - t.ENTRY_TIME)) * 60 + " +
         "    EXTRACT(MINUTE FROM (CURRENT_TIMESTAMP - t.ENTRY_TIME)) > :maxMinutes",
         countQuery = "SELECT COUNT(*) FROM TRANSACTIONS t " +
               "WHERE t.STATUS = 'ACTIVE' " +
               "AND EXTRACT(DAY FROM (CURRENT_TIMESTAMP - t.ENTRY_TIME)) * 24 * 60 + " +
               "    EXTRACT(HOUR FROM (CURRENT_TIMESTAMP - t.ENTRY_TIME)) * 60 + " +
               "    EXTRACT(MINUTE FROM (CURRENT_TIMESTAMP - t.ENTRY_TIME)) > :maxMinutes",
         nativeQuery = true)
   Page<TransactionEntity> findOverdue(@Param("maxMinutes") int maxMinutes, Pageable pageable);

   @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
         "FROM TransactionEntity t WHERE t.vehicleId = :vehicleId AND t.status = 'ACTIVE'")
   boolean existsActiveByVehicleId(@Param("vehicleId") Long vehicleId);

   @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.zoneId = :zoneId AND t.status = 'ACTIVE'")
   long countActiveByZoneId(@Param("zoneId") Long zoneId);

   @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
         "FROM TransactionEntity t WHERE t.spaceId = :spaceId AND t.status = 'ACTIVE'")
   boolean existsActiveBySpaceId(@Param("spaceId") Long spaceId);

   @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.parkingId = :parkingId AND t.status = 'ACTIVE'")
   long countActiveByParkingId(@Param("parkingId") Long parkingId);

   // ========================= POR PLACA Y POR CAPTURA =========================

   /** Estadía en curso de una placa (ya normalizada: ABC-123) */
   @Query("""
         SELECT t FROM TransactionEntity t, VehicleEntity v
         WHERE v.id = t.vehicleId AND v.licensePlate = :plate AND t.status = 'ACTIVE'
         """)
   Optional<TransactionEntity> findActiveByPlate(@Param("plate") String plate);

   /**
    * Búsqueda parcial por placa sin el guion: "abc1" encuentra ABC-123. El término llega solo con
    * letras y números, así que no hace falta escapar comodines.
    * @param status null = cualquier estado
    */
   @Query(value = """
         SELECT t FROM TransactionEntity t, VehicleEntity v
         WHERE v.id = t.vehicleId
           AND REPLACE(v.licensePlate, '-', '') LIKE CONCAT('%', :term, '%')
           AND (:status IS NULL OR t.status = :status)
         """,
         countQuery = """
         SELECT COUNT(t) FROM TransactionEntity t, VehicleEntity v
         WHERE v.id = t.vehicleId
           AND REPLACE(v.licensePlate, '-', '') LIKE CONCAT('%', :term, '%')
           AND (:status IS NULL OR t.status = :status)
         """)
   Page<TransactionEntity> searchByPlate(@Param("term") String term, @Param("status") String status,
                                         Pageable pageable);

   Optional<TransactionEntity> findByEntryCaptureId(String entryCaptureId);

   Optional<TransactionEntity> findByExitCaptureId(String exitCaptureId);

   // ========================= HISTORIAL COMPLETO =========================

   @Query("SELECT t FROM TransactionEntity t " +
         "WHERE t.entryTime >= :startDate AND t.entryTime <= :endDate")
   Page<TransactionEntity> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate,
                                           Pageable pageable);

   @Query("SELECT t FROM TransactionEntity t WHERE t.status = :status")
   Page<TransactionEntity> findByStatus(@Param("status") String status, Pageable pageable);

   @Query("SELECT t FROM TransactionEntity t WHERE t.paymentStatus = :paymentStatus")
   Page<TransactionEntity> findByPaymentStatus(@Param("paymentStatus") String paymentStatus, Pageable pageable);

   @Query("SELECT t FROM TransactionEntity t WHERE t.zoneId = :zoneId")
   Page<TransactionEntity> findByZoneId(@Param("zoneId") Long zoneId, Pageable pageable);
}