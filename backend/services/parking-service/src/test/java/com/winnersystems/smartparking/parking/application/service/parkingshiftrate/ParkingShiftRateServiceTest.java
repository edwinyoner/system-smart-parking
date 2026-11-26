package com.winnersystems.smartparking.parking.application.service.parkingshiftrate;

import com.winnersystems.smartparking.parking.application.dto.command.ConfigureParkingShiftRatesCommand;
import com.winnersystems.smartparking.parking.application.dto.command.ConfigureParkingShiftRatesCommand.Item;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingShiftRateDto;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.ParkingShiftRate;
import com.winnersystems.smartparking.parking.domain.model.Rate;
import com.winnersystems.smartparking.parking.domain.model.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ParkingShiftRateServiceTest {

   private ParkingShiftRatePersistencePort configs;
   private ShiftPersistencePort shifts;
   private RatePersistencePort rates;
   private ParkingShiftRateService service;
   private ParkingScheduleResolver resolver;
   private OperatorAssignmentPersistencePort assignments;
   private final Map<Long, Shift> shiftById = new HashMap<>();
   private final Map<Long, Rate> rateById = new HashMap<>();
   private final List<ParkingShiftRate> stored = new ArrayList<>();

   @BeforeEach
   void setUp() {
      configs = mock(ParkingShiftRatePersistencePort.class);
      ParkingPersistencePort parkings = mock(ParkingPersistencePort.class);
      shifts = mock(ShiftPersistencePort.class);
      rates = mock(RatePersistencePort.class);
      resolver = new ParkingScheduleResolver(configs, shifts, rates);
      assignments = mock(OperatorAssignmentPersistencePort.class);
      // 15:30 hora de Lima
      Clock clock = Clock.fixed(Instant.parse("2026-11-26T20:30:00Z"), ZoneId.of("America/Lima"));
      service = new ParkingShiftRateService(configs, parkings, shifts, rates, resolver, assignments, () -> Optional.of(7L), clock);

      Parking parking = Parking.create("Parqueo San Martín", "PSM", null, "Jr. San Martín", null, null, 1L);
      parking.setId(1L);
      when(parkings.findById(1L)).thenReturn(Optional.of(parking));

      shift(1L, "06:00", "13:59");
      shift(2L, "14:00", "21:59");
      shift(3L, "22:00", "05:59");
      shift(4L, "12:00", "17:59");
      rate(10L, "2.00");
      rate(11L, "3.00");

      when(shifts.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(shiftById.get((Long) i.getArgument(0))).filter(s -> !s.isDeleted()));
      when(shifts.findByIdIncludingDeleted(anyLong())).thenAnswer(i -> Optional.ofNullable(shiftById.get((Long) i.getArgument(0))));
      when(rates.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(rateById.get((Long) i.getArgument(0))).filter(r -> !r.isDeleted()));
      when(rates.findByIdIncludingDeleted(anyLong())).thenAnswer(i -> Optional.ofNullable(rateById.get((Long) i.getArgument(0))));

      AtomicLong ids = new AtomicLong(100);
      when(configs.saveAll(any())).thenAnswer(i -> {
         List<ParkingShiftRate> list = i.getArgument(0);
         list.forEach(c -> c.setId(ids.incrementAndGet()));
         stored.clear();
         stored.addAll(list);
         return list;
      });
      when(configs.save(any())).thenAnswer(i -> i.getArgument(0));
      when(configs.findByParkingId(1L)).thenAnswer(i -> List.copyOf(stored));
      when(configs.findById(anyLong())).thenAnswer(i -> stored.stream()
            .filter(c -> c.getId().equals(i.getArgument(0))).findFirst());
   }

   private void shift(Long id, String from, String to) {
      Shift s = Shift.create("Turno " + id, "T" + id, LocalTime.parse(from), LocalTime.parse(to), null, true, 1L);
      s.setId(id);
      shiftById.put(id, s);
   }

   private void rate(Long id, String amount) {
      Rate r = Rate.create("Tarifa " + id, null, new BigDecimal(amount), Rate.BillingRules.defaults(), true, 1L);
      r.setId(id);
      rateById.put(id, r);
   }

   private List<ParkingShiftRateDto> configure(Item... items) {
      return service.configureShiftRates(new ConfigureParkingShiftRatesCommand(1L, List.of(items)));
   }

   @Test
   @DisplayName("configurar: reemplaza todo y devuelve ordenado por hora de inicio")
   void configure() {
      List<ParkingShiftRateDto> result = configure(new Item(3L, 11L, null), new Item(1L, 10L, true), new Item(2L, 10L, true));

      assertThat(result).extracting(ParkingShiftRateDto::shiftId).containsExactly(1L, 2L, 3L);
      assertThat(result).allMatch(ParkingShiftRateDto::usable);
      assertThat(result.get(2).crossesMidnight()).isTrue();
      verify(configs).deleteByParkingId(1L);
   }

   @Test
   @DisplayName("turnos activos que se solapan, turno repetido o inactivo: no se guarda nada")
   void invalid() {
      assertThatThrownBy(() -> configure(new Item(1L, 10L, true), new Item(4L, 11L, true)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("se solapan");
      assertThatThrownBy(() -> configure(new Item(1L, 10L, true), new Item(1L, 11L, false)))
            .isInstanceOf(ParkingDomainException.class);
      shiftById.get(2L).deactivate();
      assertThatThrownBy(() -> configure(new Item(2L, 10L, true)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("inactivo");
      assertThatThrownBy(() -> configure(new Item(9L, 10L, true))).isInstanceOf(ResourceNotFoundException.class);
      verify(configs, never()).saveAll(any());

      // Un solape inactivo sí se guarda: el parqueo no lo usa hasta activarlo
      shiftById.get(2L).activate();
      assertThat(configure(new Item(1L, 10L, true), new Item(4L, 11L, false))).hasSize(2);
   }

   @Test
   @DisplayName("activar un turno que se solapa con otro activo: 409")
   void toggle() {
      configure(new Item(1L, 10L, true), new Item(4L, 11L, false));
      Long overlapping = stored.get(1).getId();
      assertThatThrownBy(() -> service.toggleShiftRateStatus(overlapping)).hasMessageContaining("se solapan");

      service.toggleShiftRateStatus(stored.get(0).getId());
      assertThat(service.toggleShiftRateStatus(overlapping).status()).isTrue();
   }

   @Test
   @DisplayName("horario actual: a las 15:30 aplica el turno de la tarde; sin turno, no atiende")
   void current() {
      configure(new Item(1L, 10L, true), new Item(2L, 11L, true));
      var now = service.getCurrentSchedule(1L);
      assertThat(now.open()).isTrue();
      assertThat(now.current().shiftId()).isEqualTo(2L);
      assertThat(now.current().rateAmount()).isEqualByComparingTo("3.00");

      rateById.get(11L).deactivate();
      assertThat(service.getCurrentSchedule(1L).open()).isFalse();

      configure(new Item(1L, 10L, true));
      assertThat(service.getCurrentSchedule(1L).open()).isFalse();
   }

   @Test
   @DisplayName("noche: el turno de 22:00 a 05:59 aplica a las 02:00")
   void night() {
      configure(new Item(3L, 10L, true));
      assertThat(resolver.resolve(1L, LocalDateTime.of(2026, 11, 27, 2, 0))).isPresent();
      assertThat(resolver.resolve(1L, LocalDateTime.of(2026, 11, 27, 6, 0))).isEmpty();
   }

   @Test
   @DisplayName("no se quita un turno con operadores asignados")
   void operatorsAssigned() {
      configure(new Item(1L, 10L, true), new Item(2L, 10L, true));
      when(assignments.countCurrentOrFutureByParkingAndShift(eq(1L), eq(2L), any())).thenReturn(1L);
      assertThatThrownBy(() -> configure(new Item(1L, 10L, true)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("operador asignado");
      assertThatThrownBy(() -> service.deleteShiftRateConfig(stored.get(1).getId()))
            .isInstanceOf(ResourceConflictException.class);
      // Desactivarlo sí se permite
      assertThat(configure(new Item(1L, 10L, true), new Item(2L, 10L, false))).hasSize(2);
   }

   @Test
   @DisplayName("cambiar la tarifa de un turno activo exige una tarifa activa")
   void changeRate() {
      configure(new Item(1L, 10L, true));
      Long id = stored.get(0).getId();
      rateById.get(11L).deactivate();
      assertThatThrownBy(() -> service.changeRate(id, 11L)).isInstanceOf(ResourceConflictException.class);
      rateById.get(11L).activate();
      assertThat(service.changeRate(id, 11L).rateId()).isEqualTo(11L);
   }
}
