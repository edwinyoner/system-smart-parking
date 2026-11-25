package com.winnersystems.smartparking.parking.application.service.rate;

import com.winnersystems.smartparking.parking.application.dto.command.CreateRateCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateRateCommand;
import com.winnersystems.smartparking.parking.application.dto.query.RateDto;
import com.winnersystems.smartparking.parking.application.dto.query.RateSimulationDto;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingShiftRatePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.RatePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Rate;
import com.winnersystems.smartparking.parking.domain.model.Rate.BillingRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class RateServiceTest {

   private static final Long USER = 20L;

   private RatePersistencePort rates;
   private ParkingShiftRatePersistencePort configs;
   private RateService service;

   @BeforeEach
   void setUp() {
      rates = mock(RatePersistencePort.class);
      configs = mock(ParkingShiftRatePersistencePort.class);
      CurrentUserPort currentUser = mock(CurrentUserPort.class);
      when(currentUser.requireCurrentUserId()).thenReturn(USER);
      when(configs.findParkingNamesUsingRate(anyLong())).thenReturn(List.of());
      when(rates.findByNameIncludingDeleted(any())).thenReturn(Optional.empty());
      when(rates.save(any())).thenAnswer(inv -> {
         Rate r = inv.getArgument(0);
         if (r.getId() == null) r.setId(1L);
         return r;
      });
      service = new RateService(rates, configs, currentUser);
   }

   private static Rate existing(Long id, String name) {
      Rate r = Rate.create(name, null, new BigDecimal("2.00"), BillingRules.defaults(), true, 1L);
      r.setId(id);
      return r;
   }

   @Test
   @DisplayName("crear: activa por defecto, reglas por defecto y auditoría del usuario")
   void create() {
      RateDto dto = service.createRate(new CreateRateCommand("Auto", null, new BigDecimal("2.50"),
            null, null, null, null, null));
      assertThat(dto.status()).isTrue();
      assertThat(dto.billingUnitMinutes()).isEqualTo(60);
      assertThat(dto.graceMinutes()).isEqualTo(5);
      assertThat(dto.toleranceMinutes()).isEqualTo(5);
      assertThat(dto.currency()).isEqualTo("PEN");
      assertThat(dto.createdBy()).isEqualTo(USER);
   }

   @Test
   @DisplayName("crear: nombre de una tarifa eliminada sugiere restaurarla")
   void duplicateDeletedName() {
      Rate deleted = existing(4L, "Auto");
      deleted.markAsDeleted(1L);
      when(rates.findByNameIncludingDeleted("Auto")).thenReturn(Optional.of(deleted));
      assertThatThrownBy(() -> service.createRate(new CreateRateCommand("Auto", null, BigDecimal.ONE,
            null, null, null, null, true)))
            .isInstanceOf(ResourceConflictException.class)
            .hasMessageContaining("Restáurala desde Eliminadas");
   }

   @Test
   @DisplayName("actualizar: puede conservar su propio nombre")
   void updateKeepsName() {
      Rate r = existing(1L, "Auto");
      when(rates.findById(1L)).thenReturn(Optional.of(r));
      when(rates.findByNameIncludingDeleted("Auto")).thenReturn(Optional.of(r));
      RateDto dto = service.updateRate(1L, new UpdateRateCommand("Auto", null, new BigDecimal("3.00"),
            30, null, 10, 5, null));
      assertThat(dto.amount()).isEqualByComparingTo("3.00");
      assertThat(dto.billingUnitMinutes()).isEqualTo(30);
      assertThat(dto.updatedBy()).isEqualTo(USER);
   }

   @Test
   @DisplayName("eliminar: tarifa en uso responde conflicto")
   void deleteInUse() {
      when(rates.findById(1L)).thenReturn(Optional.of(existing(1L, "Auto")));
      when(configs.findParkingNamesUsingRate(1L)).thenReturn(List.of("San Martín"));
      assertThatThrownBy(() -> service.deleteRate(1L))
            .isInstanceOf(ResourceConflictException.class)
            .hasMessageContaining("el parqueo San Martín");
      verify(rates, never()).save(any());
   }

   @Test
   @DisplayName("restaurar: desde la papelera; inexistente responde 404")
   void restore() {
      Rate r = existing(2L, "Moto");
      r.markAsDeleted(1L);
      when(rates.findDeletedById(2L)).thenReturn(Optional.of(r));
      assertThat(service.restoreRate(2L).status()).isTrue();
      when(rates.findDeletedById(3L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.restoreRate(3L)).isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   @DisplayName("simular: mismas reglas que la salida, ordenado y sin guardar")
   void simulate() {
      RateSimulationDto sim = service.simulate(new BigDecimal("2.00"), BillingRules.defaults(), List.of(66, 4, 65, 5));
      assertThat(sim.rows()).extracting(RateSimulationDto.Row::minutes).containsExactly(4, 5, 65, 66);
      assertThat(sim.rows()).extracting(RateSimulationDto.Row::amount)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("0.00"), new BigDecimal("2.00"), new BigDecimal("2.00"), new BigDecimal("4.00"));
      verify(rates, never()).save(any());
   }
}
