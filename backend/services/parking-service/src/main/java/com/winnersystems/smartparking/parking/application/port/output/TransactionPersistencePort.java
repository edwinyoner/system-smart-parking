package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.domain.model.Transaction;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Puerto de salida para persistencia de Transaction.
 *
 * Define las operaciones de persistencia que necesita el dominio
 * sin acoplarse a la tecnología de persistencia específica.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public interface TransactionPersistencePort {

   // ========================= WRITE =========================

   Transaction save(Transaction transaction);

   void delete(Long id);

   // ========================= FIND ÚNICO =========================

   Optional<Transaction> findById(Long id);

   Optional<Transaction> findActiveByVehicleId(Long vehicleId);

   Optional<Transaction> findActiveByPlateNumber(String plateNumber);

   /** Estadía creada por esa captura de cámara (idempotencia de la entrada) */
   Optional<Transaction> findByEntryCaptureId(String captureId);

   /** Estadía cerrada por esa captura de cámara (idempotencia de la salida) */
   Optional<Transaction> findByExitCaptureId(String captureId);

   // ========================= EXISTS / COUNT =========================

   boolean existsActiveByVehicleId(Long vehicleId);

   long countActiveByZoneId(Long zoneId);

   /** Hay un vehículo registrado dentro de este espacio (transacción ACTIVE) */
   boolean existsActiveBySpaceId(Long spaceId);

   /** Vehículos dentro del parqueo (transacciones ACTIVE) */
   long countActiveByParkingId(Long parkingId);

   /** Estadías en curso de un parqueo con su placa, para el mapa de ocupación (una consulta) */
   java.util.List<com.winnersystems.smartparking.parking.application.dto.query.ActiveStayView> findActiveStaysByParkingId(Long parkingId);

   // ========================= LIST ACTIVAS (paginado) =========================

   PageResult<Transaction> findAllActive(PageRequest pageRequest);

   /**
    * Estadías en curso con filtros opcionales (null = sin filtro). La placa se busca en parte
    * y sin guion: "abc1" encuentra ABC-123.
    */
   PageResult<Transaction> findActiveFiltered(Long parkingId, Long zoneId, String plate, PageRequest pageRequest);

   PageResult<Transaction> findActiveByZoneId(Long zoneId, PageRequest pageRequest);

   PageResult<Transaction> searchActiveByPlate(String plateNumber, PageRequest pageRequest);

   PageResult<Transaction> findOverdue(int maxMinutes, PageRequest pageRequest);

   // ========================= LIST HISTÓRICO (paginado) =========================

   PageResult<Transaction> findAll(PageRequest pageRequest);

   PageResult<Transaction> findByDateRange(LocalDateTime startDate, LocalDateTime endDate,
                                           PageRequest pageRequest);

   PageResult<Transaction> findByStatus(String status, PageRequest pageRequest);

   PageResult<Transaction> findByPaymentStatus(String paymentStatus, PageRequest pageRequest);

   PageResult<Transaction> findByZoneId(Long zoneId, PageRequest pageRequest);

   PageResult<Transaction> searchByPlate(String plateNumber, PageRequest pageRequest);

   // ========================= HISTORIAL (clientes y vehículos) =========================

   /** Estadías de un cliente o de un vehículo (el otro filtro en null), de la más reciente a la más antigua */
   PageResult<Transaction> findHistory(Long customerId, Long vehicleId, PageRequest pageRequest);

   /** Cantidad de estadías y total pagado (neto) de un cliente o de un vehículo */
   HistoryTotals historyTotals(Long customerId, Long vehicleId);

   /** Estadía en curso de cada vehículo: vehicleId -> transactionId */
   java.util.Map<Long, Long> findActiveStayIdsByVehicleIds(java.util.Collection<Long> vehicleIds);

   record HistoryTotals(long count, java.math.BigDecimal amount) {
   }

   /**
    * Estadías que estuvieron dentro en algún momento de [from, to), más las canceladas que
    * entraron en ese intervalo (para contarlas aparte). Para los reportes.
    *
    * @param parkingId null = todos los parqueos
    */
   java.util.List<com.winnersystems.smartparking.parking.application.dto.query.StayWindow> findStayWindows(
         java.time.LocalDateTime from, java.time.LocalDateTime to, Long parkingId);
}
