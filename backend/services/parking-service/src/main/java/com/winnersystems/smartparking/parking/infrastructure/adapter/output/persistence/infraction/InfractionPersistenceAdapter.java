package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.infraction;

import com.winnersystems.smartparking.parking.application.dto.query.InfractionFilter;
import com.winnersystems.smartparking.parking.application.dto.query.InfractionSummaryDto;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.port.output.InfractionPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.model.Infraction;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.infraction.entity.InfractionEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.infraction.mapper.InfractionPersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.infraction.repository.InfractionRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class InfractionPersistenceAdapter implements InfractionPersistencePort {

   private final InfractionRepository repository;
   private final InfractionPersistenceMapper mapper;

   public InfractionPersistenceAdapter(InfractionRepository repository, InfractionPersistenceMapper mapper) {
      this.repository = repository;
      this.mapper = mapper;
   }

   @Override
   public Infraction save(Infraction infraction) {
      return mapper.toDomain(repository.save(mapper.toEntity(infraction)));
   }

   @Override
   public Optional<Infraction> findById(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public PageResult<Infraction> search(InfractionFilter f, LocalDate today, PageRequest pageRequest) {
      // El estado que ve el usuario se traduce a estado guardado + tipo de resolución
      String status = null;
      String resolution = null;
      boolean overdue = false;
      String requested = f.status() == null ? null : f.status().trim().toUpperCase();
      if (requested != null && !requested.isEmpty() && !"ALL".equals(requested)) {
         switch (requested) {
            case InfractionFilter.OPEN, Infraction.STATUS_PENDING -> status = Infraction.STATUS_PENDING;
            case InfractionFilter.OVERDUE -> overdue = true;
            case InfractionFilter.RESOLVED -> status = Infraction.STATUS_RESOLVED;
            case Infraction.RESOLUTION_PAID, Infraction.RESOLUTION_DISMISSED, Infraction.RESOLUTION_WARNING -> {
               status = Infraction.STATUS_RESOLVED;
               resolution = requested;
            }
            default -> throw new ParkingDomainException("Estado no válido: " + f.status());
         }
      }
      String type = f.infractionType() == null || f.infractionType().isBlank() ? null
            : f.infractionType().trim().toUpperCase();
      String term = f.search() == null ? null : f.search().toUpperCase().replaceAll("[^A-Z0-9]", "");
      LocalDateTime fromTime = f.from() != null ? f.from().atStartOfDay() : null;
      LocalDateTime toTime = f.to() != null ? f.to().plusDays(1).atStartOfDay() : null;

      Page<InfractionEntity> page = repository.search(status, resolution, overdue, today, f.parkingId(), type,
            term == null || term.isEmpty() ? null : term, fromTime, toTime,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
      return toPageResult(page);
   }

   @Override
   public List<Infraction> findByTransactionId(Long transactionId) {
      return repository.findByTransactionIdOrderByDetectedAtDesc(transactionId).stream().map(mapper::toDomain).toList();
   }

   @Override
   public PageResult<Infraction> findByVehicleId(Long vehicleId, PageRequest pageRequest) {
      return toPageResult(repository.findByVehicleIdOrderByDetectedAtDesc(vehicleId,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size())));
   }

   @Override
   public InfractionSummaryDto summary(Long parkingId, LocalDate today) {
      Object[] counts = firstRow(repository.countByState(parkingId, today), 5);
      Object[] totals = firstRow(repository.pendingFineTotals(parkingId), 2);
      BigDecimal pending = decimal(totals[0]).subtract(decimal(totals[1]));
      return new InfractionSummaryDto(number(counts[0]), number(counts[1]), number(counts[2]), number(counts[3]),
            number(counts[4]), pending.signum() < 0 ? BigDecimal.ZERO : pending);
   }

   private PageResult<Infraction> toPageResult(Page<InfractionEntity> page) {
      return new PageResult<>(page.getContent().stream().map(mapper::toDomain).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements());
   }

   private static Object[] firstRow(List<Object[]> rows, int columns) {
      return rows.isEmpty() || rows.get(0) == null ? new Object[columns] : rows.get(0);
   }

   private static long number(Object value) {
      return value instanceof Number n ? n.longValue() : 0L;
   }

   private static BigDecimal decimal(Object value) {
      if (value instanceof BigDecimal b) {
         return b;
      }
      return value instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
   }

   @Override
   public PageResult<Infraction> findByCustomerId(Long customerId, PageRequest pageRequest) {
      return toPageResult(repository.findByCustomerIdOrderByDetectedAtDesc(customerId,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size())));
   }

   @Override
   public com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort.HistoryTotals
   historyTotals(Long customerId, Long vehicleId) {
      Object[] r = firstRow(repository.historyTotals(customerId, vehicleId), 3);
      BigDecimal pending = decimal(r[1]).subtract(decimal(r[2]));
      return new com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort.HistoryTotals(
            number(r[0]), pending.signum() < 0 ? BigDecimal.ZERO : pending);
   }

   @Override
   public List<ZoneCount> countRegisteredBetween(java.time.LocalDateTime from, java.time.LocalDateTime to, Long parkingId) {
      return repository.countRegisteredBetween(from, to, parkingId).stream()
            .map(r -> new ZoneCount((Long) r[0], (Long) r[1], ((Number) r[2]).longValue()))
            .toList();
   }
}
