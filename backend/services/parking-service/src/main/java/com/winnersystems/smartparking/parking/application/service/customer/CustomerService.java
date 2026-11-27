package com.winnersystems.smartparking.parking.application.service.customer;

import com.winnersystems.smartparking.parking.application.dto.command.UpdateCustomerContactCommand;
import com.winnersystems.smartparking.parking.application.dto.query.*;
import com.winnersystems.smartparking.parking.application.port.input.customer.ManageCustomersUseCase;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.application.service.history.StayHistoryAssembler;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Clientes: consulta, historial y datos de contacto.
 * Nacen solos en la entrada (con documento); no se crean ni se eliminan a mano.
 */
@Service
@Transactional
public class CustomerService implements ManageCustomersUseCase {

   private final CustomerPersistencePort customerPort;
   private final CustomerVehiclePersistencePort customerVehiclePort;
   private final VehiclePersistencePort vehiclePort;
   private final TransactionPersistencePort transactionPort;
   private final DocumentTypePersistencePort documentTypePort;
   private final StayHistoryAssembler history;
   private final CurrentUserPort currentUserPort;
   private final Clock clock;

   public CustomerService(CustomerPersistencePort customerPort,
                          CustomerVehiclePersistencePort customerVehiclePort,
                          VehiclePersistencePort vehiclePort,
                          TransactionPersistencePort transactionPort,
                          DocumentTypePersistencePort documentTypePort,
                          StayHistoryAssembler history,
                          CurrentUserPort currentUserPort,
                          Clock clock) {
      this.customerPort = customerPort;
      this.customerVehiclePort = customerVehiclePort;
      this.vehiclePort = vehiclePort;
      this.transactionPort = transactionPort;
      this.documentTypePort = documentTypePort;
      this.history = history;
      this.currentUserPort = currentUserPort;
      this.clock = clock;
   }

   @Override
   @Transactional(readOnly = true)
   public PageResult<CustomerDto> list(String search, PageRequest pageRequest) {
      String term = search == null || search.isBlank() ? null : search.trim();
      PageResult<Customer> page = customerPort.findAll(pageRequest, term, "ACTIVE");
      Map<Long, Long> vehicleCounts = customerVehiclePort.countVehiclesByCustomerIds(
            page.content().stream().map(Customer::getId).toList());
      Map<Long, String> docCodes = new HashMap<>();
      return new PageResult<>(page.content().stream()
            .map(c -> toDto(c, vehicleCounts.getOrDefault(c.getId(), 0L), docCodes)).toList(),
            page.pageNumber(), page.pageSize(), page.totalElements());
   }

   @Override
   @Transactional(readOnly = true)
   public CustomerDetailDto get(Long customerId) {
      Customer c = load(customerId);
      List<CustomerVehicle> links = customerVehiclePort.findByCustomerId(customerId);
      Map<Long, Long> active = transactionPort.findActiveStayIdsByVehicleIds(
            links.stream().map(CustomerVehicle::getVehicleId).toList());
      List<CustomerDetailDto.LinkedVehicle> vehicles = links.stream()
            .sorted(Comparator.comparing(CustomerVehicle::getUsageCount).reversed())
            .map(l -> new CustomerDetailDto.LinkedVehicle(l.getVehicleId(),
                  vehiclePort.findById(l.getVehicleId()).map(Vehicle::getLicensePlate).orElse(null),
                  l.getUsageCount(), active.get(l.getVehicleId())))
            .toList();
      return new CustomerDetailDto(toDto(c, vehicles.size(), new HashMap<>()), vehicles,
            history.totals(customerId, null));
   }

   @Override
   @Transactional(readOnly = true)
   public PageResult<StayHistoryDto> stays(Long customerId, PageRequest pageRequest) {
      load(customerId);
      return history.page(transactionPort.findHistory(customerId, null, pageRequest));
   }

   @Override
   public CustomerDto updateContact(Long customerId, UpdateCustomerContactCommand command) {
      Customer c = load(customerId);
      c.changeContact(command.phone(), command.email(), command.address(),
            currentUserPort.requireCurrentUserId(), LocalDateTime.now(clock));
      Customer saved = customerPort.save(c);
      return toDto(saved, customerVehiclePort.countByCustomerId(customerId), new HashMap<>());
   }

   private Customer load(Long id) {
      return customerPort.findById(id)
            .filter(c -> !c.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + id));
   }

   private CustomerDto toDto(Customer c, long vehicleCount, Map<Long, String> docCodes) {
      String docCode = c.getDocumentTypeId() == null ? null : docCodes.computeIfAbsent(c.getDocumentTypeId(),
            id -> documentTypePort.findByIdIncludingDeleted(id).map(DocumentType::getCode).orElse(null));
      return new CustomerDto(c.getId(), c.getDocumentTypeId(), docCode, c.getDocumentNumber(),
            c.getFirstName(), c.getLastName(), c.getFullName(), c.getPhone(), c.getEmail(), c.getAddress(),
            c.getTotalVisits() == null ? 0 : c.getTotalVisits(), vehicleCount,
            c.getFirstSeenDate(), c.getLastSeenDate(), c.getCreatedAt(), c.getUpdatedAt());
   }
}
