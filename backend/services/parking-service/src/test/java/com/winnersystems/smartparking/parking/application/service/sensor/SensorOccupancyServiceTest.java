package com.winnersystems.smartparking.parking.application.service.sensor;

import com.winnersystems.smartparking.parking.application.dto.command.SensorReadingCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.SensorReadingResult;
import com.winnersystems.smartparking.parking.application.port.output.SpacePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Space;
import com.winnersystems.smartparking.parking.domain.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El sensor corrige el espacio asignado al azar en la entrada y libera los espacios
 * ocupados sin estadía (calle).
 */
class SensorOccupancyServiceTest {

   private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 10, 0);
   private static final ZoneId LIMA = ZoneId.of("America/Lima");
   private static final Long ZONE = 1L;

   private final SpacePersistencePort spacePort = mock(SpacePersistencePort.class);
   private final TransactionPersistencePort transactionPort = mock(TransactionPersistencePort.class);
   private final Map<Long, Space> spaces = new HashMap<>();
   private final List<Transaction> stays = new ArrayList<>();
   private SensorOccupancyService service;

   @BeforeEach
   void setUp() {
      service = new SensorOccupancyService(spacePort, transactionPort, Clock.fixed(NOW.atZone(LIMA).toInstant(), LIMA));
      when(spacePort.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(spaces.get(i.<Long>getArgument(0))));
      when(spacePort.findByIdIncludingDeleted(anyLong())).thenAnswer(i -> Optional.ofNullable(spaces.get(i.<Long>getArgument(0))));
      when(spacePort.findBySensorIds(anyCollection())).thenAnswer(i -> {
         Collection<String> ids = i.getArgument(0);
         return spaces.values().stream().filter(s -> s.getSensorId() != null && ids.contains(s.getSensorId())).toList();
      });
      when(spacePort.save(any())).thenAnswer(i -> i.getArgument(0));
      when(transactionPort.save(any())).thenAnswer(i -> i.getArgument(0));
      when(transactionPort.findActiveByZoneId(eq(ZONE), any()))
            .thenAnswer(i -> new PageResult<>(List.copyOf(stays), 0, 100, stays.size()));
   }

   @Test
   void sensorDetectaVehiculoYMueveLaEstadiaAsignadaAlAzar() {
      Space random = space(3L, "A-03", "S3");     // asignado al azar en la entrada
      Space real = space(1L, "A-01", "S1");       // donde de verdad estacionó
      random.occupy();
      stays.add(stay(10L, random.getId(), NOW.minusMinutes(5)));

      SensorReadingResult r = service.recordReading(new SensorReadingCommand("S1", true));

      assertThat(r.outcome()).isEqualTo(SensorReadingResult.STAY_MOVED);
      assertThat(r.transactionId()).isEqualTo(10L);
      assertThat(stays.get(0).getSpaceId()).isEqualTo(1L);
      assertThat(real.isOccupied()).isTrue();
      assertThat(random.isAvailable()).isTrue();
      assertThat(real.sensorDetectsVehicle()).isTrue();
      assertThat(real.getSensorReadAt()).isEqualTo(NOW);
   }

   @Test
   void noMueveUnaEstadiaQueSuSensorYaConfirma() {
      Space confirmed = space(3L, "A-03", "S3");
      confirmed.occupy();
      confirmed.recordSensorReading(true, NOW.minusMinutes(1));
      stays.add(stay(10L, confirmed.getId(), NOW.minusMinutes(5)));
      Space other = space(1L, "A-01", "S1");

      SensorReadingResult r = service.recordReading(new SensorReadingCommand("S1", true));

      // Otro vehículo sin estadía (por ejemplo, entró sin registrarse): solo se marca ocupado
      assertThat(r.outcome()).isEqualTo(SensorReadingResult.SPACE_OCCUPIED);
      assertThat(stays.get(0).getSpaceId()).isEqualTo(3L);
      assertThat(other.isOccupied()).isTrue();
   }

   @Test
   void mueveLaEstadiaMasAntigua() {
      Space a = space(2L, "A-02", null);
      Space b = space(3L, "A-03", null);
      a.occupy();
      b.occupy();
      stays.add(stay(20L, a.getId(), NOW.minusMinutes(30)));
      stays.add(stay(21L, b.getId(), NOW.minusMinutes(2)));
      space(1L, "A-01", "S1");

      SensorReadingResult r = service.recordReading(new SensorReadingCommand("S1", true));

      assertThat(r.transactionId()).isEqualTo(20L);
   }

   @Test
   void sinVehiculoYSinEstadiaSeLibera() {
      Space street = space(1L, "A-01", "S1");
      street.occupy();

      SensorReadingResult r = service.recordReading(new SensorReadingCommand("S1", false));

      assertThat(r.outcome()).isEqualTo(SensorReadingResult.SPACE_RELEASED);
      assertThat(street.isAvailable()).isTrue();
   }

   @Test
   void sinVehiculoPeroConEstadiaSigueOcupadoHastaLaSalida() {
      Space s = space(1L, "A-01", "S1");
      s.occupy();
      stays.add(stay(10L, s.getId(), NOW.minusMinutes(20)));

      SensorReadingResult r = service.recordReading(new SensorReadingCommand("S1", false));

      assertThat(r.outcome()).isEqualTo(SensorReadingResult.NO_CHANGE);
      assertThat(s.isOccupied()).isTrue();
      assertThat(s.sensorDetectsVehicle()).isFalse();
   }

   @Test
   void enMantenimientoSoloGuardaLaLectura() {
      Space s = space(1L, "A-01", "S1");
      s.changeStatus(Space.STATUS_MAINTENANCE);

      SensorReadingResult r = service.recordReading(new SensorReadingCommand("S1", true));

      assertThat(r.outcome()).isEqualTo(SensorReadingResult.NO_CHANGE);
      assertThat(s.isInMaintenance()).isTrue();
      assertThat(s.sensorDetectsVehicle()).isTrue();
   }

   @Test
   void sensorDesconocido() {
      assertThatThrownBy(() -> service.recordReading(new SensorReadingCommand("NO-EXISTE", true)))
            .isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   void cambiarElSensorBorraLaLecturaAnterior() {
      Space s = space(1L, "A-01", "S1");
      s.recordSensorReading(true, NOW);

      s.update(s.getType(), null, null, null, "S9", false);

      assertThat(s.getSensorOccupied()).isNull();
      assertThat(s.getSensorReadAt()).isNull();
   }

   private Space space(Long id, String code, String sensorId) {
      Space s = Space.create(ZONE, code, "PERPENDICULAR", null, null, null, sensorId, false, 1L);
      s.setId(id);
      spaces.put(id, s);
      return s;
   }

   private static Transaction stay(Long id, Long spaceId, LocalDateTime entry) {
      Transaction t = Transaction.registerEntry(new Transaction.EntryData(100L + id, null, 1L, ZONE,
            spaceId, 50L, null, null, Transaction.METHOD_MANUAL, 7L, null, null, null), entry);
      t.setId(id);
      return t;
   }
}
