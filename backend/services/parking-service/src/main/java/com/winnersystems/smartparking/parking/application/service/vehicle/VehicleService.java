package com.winnersystems.smartparking.parking.application.service.vehicle;

import com.winnersystems.smartparking.parking.application.dto.query.*;
import com.winnersystems.smartparking.parking.application.port.input.vehicle.ManageVehiclesUseCase;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.application.service.history.StayHistoryAssembler;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Vehículos: consulta e historial (solo lectura) */
@Service
@Transactional(readOnly = true)
public class VehicleService implements ManageVehiclesUseCase {

   private final VehiclePersistencePort vehiclePort;
   private final CustomerVehiclePersistencePort customerVehiclePort;
   private final CustomerPersistencePort customerPort;
   private final TransactionPersistencePort transactionPort;
   private final DocumentTypePersistencePort documentTypePort;
   private final StayHistoryAssembler history;

   public VehicleService(VehiclePersistencePort vehiclePort,
                         CustomerVehiclePersistencePort customerVehiclePort,
                         CustomerPersistencePort customerPort,
                         TransactionPersistencePort transactionPort,
                         DocumentTypePersistencePort documentTypePort,
                         StayHistoryAssembler history) {
      this.vehiclePort = vehiclePort;
      this.customerVehiclePort = customerVehiclePort;
      this.customerPort = customerPort;
      this.transactionPort = transactionPort;
      this.documentTypePort = documentTypePort;
      this.history = history;
   }

   @Override
   public PageResult<VehicleDto> list(String search, PageRequest pageRequest) {
      // La placa se busca sin guion ni espacios: "abc1" encuentra "ABC-123"
      String term = search == null ? null : search.toUpperCase().replaceAll("[^A-Z0-9]", "");
      PageResult<Vehicle> page = vehiclePort.findAll(pageRequest, term == null || term.isEmpty() ? null : term, "ACTIVE");
      List<Long> ids = page.content().stream().map(Vehicle::getId).toList();
      Map<Long, Long> drivers = customerVehiclePort.countCustomersByVehicleIds(ids);
      Map<Long, Long> active = transactionPort.findActiveStayIdsByVehicleIds(ids);
      return new PageResult<>(page.content().stream()
            .map(v -> toDto(v, drivers.getOrDefault(v.getId(), 0L), active.get(v.getId()))).toList(),
            page.pageNumber(), page.pageSize(), page.totalElements());
   }

   @Override
   public VehicleDetailDto get(Long vehicleId) {
      Vehicle v = load(vehicleId);
      Map<Long, String> docCodes = new HashMap<>();
      List<VehicleDetailDto.LinkedDriver> drivers = customerVehiclePort.findByVehicleId(vehicleId).stream()
            .sorted(Comparator.comparing(CustomerVehicle::getUsageCount).reversed())
            .map(l -> customerPort.findById(l.getCustomerId()).map(c -> new VehicleDetailDto.LinkedDriver(
                  c.getId(), c.getFullName(),
                  c.getDocumentTypeId() == null ? null : docCodes.computeIfAbsent(c.getDocumentTypeId(),
                        id -> documentTypePort.findByIdIncludingDeleted(id).map(DocumentType::getCode).orElse(null)),
                  c.getDocumentNumber(), l.getUsageCount())).orElse(null))
            .filter(Objects::nonNull)
            .toList();
      Transaction activeStay = transactionPort.findActiveByVehicleId(vehicleId).orElse(null);
      return new VehicleDetailDto(toDto(v, drivers.size(), activeStay != null ? activeStay.getId() : null), drivers,
            activeStay != null ? history.one(activeStay) : null, history.totals(null, vehicleId));
   }

   @Override
   public PageResult<StayHistoryDto> stays(Long vehicleId, PageRequest pageRequest) {
      load(vehicleId);
      return history.page(transactionPort.findHistory(null, vehicleId, pageRequest));
   }

   private Vehicle load(Long id) {
      return vehiclePort.findById(id)
            .filter(v -> !v.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException("Vehículo no encontrado: " + id));
   }

   private VehicleDto toDto(Vehicle v, long driverCount, Long activeStayId) {
      return new VehicleDto(v.getId(), v.getLicensePlate(), v.getTotalVisits() == null ? 0 : v.getTotalVisits(),
            driverCount, activeStayId, v.getFirstSeenDate(), v.getLastSeenDate(), v.getCreatedAt());
   }
}
