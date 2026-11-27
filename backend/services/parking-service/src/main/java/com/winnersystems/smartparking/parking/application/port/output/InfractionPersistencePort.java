package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.application.dto.query.InfractionFilter;
import com.winnersystems.smartparking.parking.application.dto.query.InfractionSummaryDto;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.domain.model.Infraction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de infracciones. No hay borrado: es un registro histórico (se anula).
 */
public interface InfractionPersistencePort {

   Infraction save(Infraction infraction);

   Optional<Infraction> findById(Long id);

   /**
    * Lista con filtros, de la más reciente a la más antigua.
    *
    * @param today día de referencia para el filtro OVERDUE
    */
   PageResult<Infraction> search(InfractionFilter filter, LocalDate today, PageRequest pageRequest);

   List<Infraction> findByTransactionId(Long transactionId);

   PageResult<Infraction> findByVehicleId(Long vehicleId, PageRequest pageRequest);

   /** Conteos por estado; parkingId null = todos los parqueos */
   InfractionSummaryDto summary(Long parkingId, LocalDate today);

   PageResult<Infraction> findByCustomerId(Long customerId, PageRequest pageRequest);

   /** Cantidad de infracciones y lo que falta cobrar de las pendientes, de un cliente o de un vehículo */
   TransactionPersistencePort.HistoryTotals historyTotals(Long customerId, Long vehicleId);

   /** Infracciones registradas en [from, to), agrupadas por parqueo y zona (zoneId puede ser null) */
   List<ZoneCount> countRegisteredBetween(java.time.LocalDateTime from, java.time.LocalDateTime to, Long parkingId);

   record ZoneCount(Long parkingId, Long zoneId, long count) {
   }
}
