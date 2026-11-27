package com.winnersystems.smartparking.parking.application.service.customer;

import com.winnersystems.smartparking.parking.application.dto.command.UpdateCustomerContactCommand;
import com.winnersystems.smartparking.parking.application.dto.query.*;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.application.service.history.StayHistoryAssembler;
import com.winnersystems.smartparking.parking.application.service.vehicle.VehicleService;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerAndVehicleServiceTest {

   static final ZoneId PERU = ZoneId.of("America/Lima");
   static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 11, 27, 10, 0).atZone(PERU).toInstant(), PERU);

   private CustomerPersistencePort customerPort;
   private CustomerService customers;
   private VehicleService vehicles;

   @BeforeEach
   void setUp() {
      customerPort = mock(CustomerPersistencePort.class);
      Customer juan = new Customer(1L, "43567890", "Juan", "Pérez");
      juan.setId(5L);
      when(customerPort.findById(5L)).thenReturn(Optional.of(juan));
      when(customerPort.save(any())).thenAnswer(i -> i.getArgument(0));
      when(customerPort.findAll(any(), any(), eq("ACTIVE")))
            .thenReturn(new PageResult<>(List.of(juan), 0, 20, 1));

      VehiclePersistencePort vehiclePort = mock(VehiclePersistencePort.class);
      Vehicle car = new Vehicle("ABC-123");
      car.setId(7L);
      Vehicle moto = new Vehicle("XYZ-789");
      moto.setId(8L);
      when(vehiclePort.findById(7L)).thenReturn(Optional.of(car));
      when(vehiclePort.findById(8L)).thenReturn(Optional.of(moto));
      when(vehiclePort.findAll(any(), any(), eq("ACTIVE")))
            .thenReturn(new PageResult<>(List.of(car, moto), 0, 20, 2));

      CustomerVehiclePersistencePort links = mock(CustomerVehiclePersistencePort.class);
      CustomerVehicle l1 = new CustomerVehicle(5L, 7L);
      l1.setUsageCount(1);
      CustomerVehicle l2 = new CustomerVehicle(5L, 8L);
      l2.setUsageCount(4);
      when(links.findByCustomerId(5L)).thenReturn(List.of(l1, l2));
      when(links.findByVehicleId(7L)).thenReturn(List.of(l1));
      when(links.countVehiclesByCustomerIds(any())).thenReturn(Map.of(5L, 2L));
      when(links.countCustomersByVehicleIds(any())).thenReturn(Map.of(7L, 1L));

      TransactionPersistencePort transactions = mock(TransactionPersistencePort.class);
      when(transactions.findActiveStayIdsByVehicleIds(any())).thenReturn(Map.of(7L, 900L));
      when(transactions.historyTotals(any(), any()))
            .thenReturn(new TransactionPersistencePort.HistoryTotals(3, new BigDecimal("12.50")));
      when(transactions.findActiveByVehicleId(anyLong())).thenReturn(Optional.empty());

      InfractionPersistencePort infractions = mock(InfractionPersistencePort.class);
      when(infractions.historyTotals(any(), any()))
            .thenReturn(new TransactionPersistencePort.HistoryTotals(1, new BigDecimal("50")));

      DocumentTypePersistencePort docs = mock(DocumentTypePersistencePort.class);
      DocumentType dni = mock(DocumentType.class);
      when(dni.getCode()).thenReturn("DNI");
      when(docs.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(dni));

      StayHistoryAssembler history = new StayHistoryAssembler(transactions, infractions,
            mock(ParkingPersistencePort.class), mock(ZonePersistencePort.class), mock(SpacePersistencePort.class),
            vehiclePort);
      CurrentUserPort user = mock(CurrentUserPort.class);
      when(user.requireCurrentUserId()).thenReturn(3L);

      customers = new CustomerService(customerPort, links, vehiclePort, transactions, docs, history, user, CLOCK);
      vehicles = new VehicleService(vehiclePort, links, customerPort, transactions, docs, history);
   }

   @Test
   @DisplayName("cliente: lista con vehículos, detalle ordenado por uso y totales del historial")
   void customerDetail() {
      PageResult<CustomerDto> page = customers.list("juan", PageRequest.of(0, 20));
      assertThat(page.content().get(0).vehicleCount()).isEqualTo(2);
      assertThat(page.content().get(0).documentTypeCode()).isEqualTo("DNI");

      CustomerDetailDto d = customers.get(5L);
      assertThat(d.vehicles()).extracting(CustomerDetailDto.LinkedVehicle::plateNumber).containsExactly("XYZ-789", "ABC-123");
      assertThat(d.vehicles().get(1).activeStayId()).isEqualTo(900L);
      assertThat(d.totals().stays()).isEqualTo(3);
      assertThat(d.totals().totalPaid()).isEqualByComparingTo("12.50");
      assertThat(d.totals().pendingFines()).isEqualByComparingTo("50");
   }

   @Test
   @DisplayName("cliente: edita contacto; inexistente da 404")
   void updateContact() {
      CustomerDto dto = customers.updateContact(5L, new UpdateCustomerContactCommand("987654321", "a@b.pe", null));
      assertThat(dto.phone()).isEqualTo("987654321");
      verify(customerPort).save(any());
      assertThatThrownBy(() -> customers.get(99L)).isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   @DisplayName("vehículo: lista con conductores y si está dentro; detalle con sus conductores")
   void vehicles() {
      PageResult<VehicleDto> page = vehicles.list("abc-1", PageRequest.of(0, 20));
      assertThat(page.content().get(0).activeStayId()).isEqualTo(900L);
      assertThat(page.content().get(0).driverCount()).isEqualTo(1);
      assertThat(page.content().get(1).activeStayId()).isNull();

      VehicleDetailDto d = vehicles.get(7L);
      assertThat(d.drivers()).singleElement().satisfies(dr -> {
         assertThat(dr.fullName()).isEqualTo("Juan Pérez");
         assertThat(dr.documentTypeCode()).isEqualTo("DNI");
      });
      assertThat(d.activeStay()).isNull();
   }
}
