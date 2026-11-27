package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.transaction;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort;
import com.winnersystems.smartparking.parking.domain.model.Transaction;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.transaction.entity.TransactionEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.transaction.mapper.TransactionPersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de persistencia para Transaction.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class TransactionPersistenceAdapter implements TransactionPersistencePort {

   private final TransactionRepository transactionRepository;
   private final TransactionPersistenceMapper transactionMapper;

   // ========================= WRITE =========================

   @Override
   public Transaction save(Transaction transaction) {
      TransactionEntity entity = transactionMapper.toEntity(transaction);
      TransactionEntity saved = transactionRepository.save(entity);
      return transactionMapper.toDomain(saved);
   }

   @Override
   public void delete(Long id) {
      transactionRepository.deleteById(id);
   }

   // ========================= FIND ÚNICO =========================

   @Override
   public Optional<Transaction> findById(Long id) {
      return transactionRepository.findById(id)
            .map(transactionMapper::toDomain);
   }

   @Override
   public Optional<Transaction> findActiveByVehicleId(Long vehicleId) {
      return transactionRepository.findActiveByVehicleId(vehicleId)
            .map(transactionMapper::toDomain);
   }

   /**
    * IMPLEMENTADO EN SERVICE LAYER
    *
    * Este método se implementa en TransactionService porque requiere:
    * 1. Buscar vehicle por placa (VehiclePersistencePort)
    * 2. Obtener vehicleId
    * 3. Buscar transaction por vehicleId
    */
   @Override
   public Optional<Transaction> findActiveByPlateNumber(String plateNumber) {
      return transactionRepository.findActiveByPlate(plateNumber).map(transactionMapper::toDomain);
   }

   @Override
   public Optional<Transaction> findByEntryCaptureId(String captureId) {
      return transactionRepository.findByEntryCaptureId(captureId).map(transactionMapper::toDomain);
   }

   @Override
   public Optional<Transaction> findByExitCaptureId(String captureId) {
      return transactionRepository.findByExitCaptureId(captureId).map(transactionMapper::toDomain);
   }

   // ========================= EXISTS / COUNT =========================

   @Override
   public boolean existsActiveByVehicleId(Long vehicleId) {
      return transactionRepository.existsActiveByVehicleId(vehicleId);
   }

   @Override
   public long countActiveByZoneId(Long zoneId) {
      return transactionRepository.countActiveByZoneId(zoneId);
   }

   @Override
   public boolean existsActiveBySpaceId(Long spaceId) {
      return transactionRepository.existsActiveBySpaceId(spaceId);
   }

   @Override
   public long countActiveByParkingId(Long parkingId) {
      return transactionRepository.countActiveByParkingId(parkingId);
   }

   // ========================= LIST ACTIVAS =========================

   @Override
   public PageResult<Transaction> findAllActive(PageRequest pageRequest) {
      return toPageResult(
            transactionRepository.findAllActive(toPageable(pageRequest))
      );
   }

   @Override
   public java.util.List<com.winnersystems.smartparking.parking.application.dto.query.ActiveStayView>
         findActiveStaysByParkingId(Long parkingId) {
      return transactionRepository.findActiveStaysByParkingId(parkingId);
   }

   @Override
   public PageResult<Transaction> findActiveFiltered(Long parkingId, Long zoneId, String plate, PageRequest pageRequest) {
      String term = plateTerm(plate);
      return toPageResult(transactionRepository.findActiveFiltered(parkingId, zoneId, term.isEmpty() ? null : term,
            toPageable(pageRequest)));
   }

   @Override
   public PageResult<Transaction> findActiveByZoneId(Long zoneId, PageRequest pageRequest) {
      return toPageResult(
            transactionRepository.findActiveByZoneId(zoneId, toPageable(pageRequest))
      );
   }

   /**
    * IMPLEMENTADO EN SERVICE LAYER
    */
   @Override
   public PageResult<Transaction> searchActiveByPlate(String plateNumber, PageRequest pageRequest) {
      return toPageResult(transactionRepository.searchByPlate(plateTerm(plateNumber), "ACTIVE",
            toPageable(pageRequest)));
   }

   @Override
   public PageResult<Transaction> findOverdue(int maxMinutes, PageRequest pageRequest) {
      return toPageResult(
            transactionRepository.findOverdue(maxMinutes, toPageable(pageRequest))
      );
   }

   // ========================= LIST HISTÓRICO =========================

   @Override
   public PageResult<Transaction> findAll(PageRequest pageRequest) {
      return toPageResult(
            transactionRepository.findAll(toPageable(pageRequest))
      );
   }

   @Override
   public PageResult<Transaction> findByDateRange(LocalDateTime startDate, LocalDateTime endDate,
                                                  PageRequest pageRequest) {
      return toPageResult(
            transactionRepository.findByDateRange(startDate, endDate, toPageable(pageRequest))
      );
   }

   @Override
   public PageResult<Transaction> findByStatus(String status, PageRequest pageRequest) {
      return toPageResult(
            transactionRepository.findByStatus(status, toPageable(pageRequest))
      );
   }

   @Override
   public PageResult<Transaction> findByPaymentStatus(String paymentStatus, PageRequest pageRequest) {
      return toPageResult(
            transactionRepository.findByPaymentStatus(paymentStatus, toPageable(pageRequest))
      );
   }

   @Override
   public PageResult<Transaction> findByZoneId(Long zoneId, PageRequest pageRequest) {
      return toPageResult(
            transactionRepository.findByZoneId(zoneId, toPageable(pageRequest))
      );
   }

   /**
    * IMPLEMENTADO EN SERVICE LAYER
    */
   @Override
   public PageResult<Transaction> searchByPlate(String plateNumber, PageRequest pageRequest) {
      return toPageResult(transactionRepository.searchByPlate(plateTerm(plateNumber), null,
            toPageable(pageRequest)));
   }

   /** Solo letras y números en mayúsculas: "abc-1" y "ABC 1" buscan lo mismo */
   private static String plateTerm(String raw) {
      return raw == null ? "" : raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
   }

   // ========================= CONVERSIÓN PRIVADA =========================

   private org.springframework.data.domain.PageRequest toPageable(PageRequest req) {
      if (req.hasSorting()) {
         Sort sort = req.isDescending()
               ? Sort.by(req.sortBy()).descending()
               : Sort.by(req.sortBy()).ascending();
         return org.springframework.data.domain.PageRequest.of(req.page(), req.size(), sort);
      }
      return org.springframework.data.domain.PageRequest.of(req.page(), req.size());
   }

   private PageResult<Transaction> toPageResult(Page<TransactionEntity> page) {
      return PageResult.of(
            page.getContent().stream()
                  .map(transactionMapper::toDomain)
                  .toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements()
      );
   }

   // ========================= HISTORIAL =========================

   @Override
   public PageResult<Transaction> findHistory(Long customerId, Long vehicleId, PageRequest pageRequest) {
      if (customerId == null && vehicleId == null) {
         throw new IllegalStateException("El historial exige un cliente o un vehículo");
      }
      return toPageResult(transactionRepository.findHistory(customerId, vehicleId,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size())));
   }

   @Override
   public HistoryTotals historyTotals(Long customerId, Long vehicleId) {
      List<Object[]> rows = transactionRepository.historyTotals(customerId, vehicleId);
      Object[] r = rows.isEmpty() ? new Object[]{0L, java.math.BigDecimal.ZERO} : rows.get(0);
      return new HistoryTotals(r[0] instanceof Number n ? n.longValue() : 0L,
            r[1] instanceof java.math.BigDecimal b ? b
                  : r[1] instanceof Number n ? new java.math.BigDecimal(n.toString()) : java.math.BigDecimal.ZERO);
   }

   @Override
   public java.util.Map<Long, Long> findActiveStayIdsByVehicleIds(java.util.Collection<Long> vehicleIds) {
      java.util.Map<Long, Long> map = new java.util.HashMap<>();
      if (vehicleIds == null || vehicleIds.isEmpty()) {
         return map;
      }
      for (Object[] r : transactionRepository.findActiveIdsByVehicleIds(vehicleIds)) {
         map.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
      }
      return map;
   }
}
