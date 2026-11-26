package com.winnersystems.smartparking.parking.application.service.parkingshiftrate;

import com.winnersystems.smartparking.parking.application.dto.command.ConfigureParkingShiftRatesCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingScheduleStatusDto;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingShiftRateDto;
import com.winnersystems.smartparking.parking.application.port.input.parkingshiftrate.ConfigureParkingShiftRatesUseCase;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.ParkingShiftRate;
import com.winnersystems.smartparking.parking.domain.model.Rate;
import com.winnersystems.smartparking.parking.domain.model.Shift;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Horarios y tarifas de cada parqueo (ParkingShiftRate). Valen para todas sus zonas.
 *
 * Reglas:
 * - El parqueo debe estar vigente (404).
 * - Un turno una sola vez por parqueo (400).
 * - Una configuración activa exige turno y tarifa activos (409), y los turnos activos
 *   del parqueo no pueden solaparse (409): a cada minuto le toca una sola tarifa.
 * - Sin configuración activa para una hora, el parqueo no atiende a esa hora.
 */
@Service
@Transactional
public class ParkingShiftRateService implements ConfigureParkingShiftRatesUseCase {

   /** Turnos por parqueo: de sobra para cubrir el día */
   public static final int MAX_CONFIGURATIONS = 24;

   private final ParkingShiftRatePersistencePort configPort;
   private final ParkingPersistencePort parkingPort;
   private final ShiftPersistencePort shiftPort;
   private final RatePersistencePort ratePort;
   private final ParkingScheduleResolver resolver;
   private final OperatorAssignmentPersistencePort assignmentPort;
   private final CurrentUserPort currentUserPort;
   private final Clock clock;

   public ParkingShiftRateService(ParkingShiftRatePersistencePort configPort,
                                  ParkingPersistencePort parkingPort,
                                  ShiftPersistencePort shiftPort,
                                  RatePersistencePort ratePort,
                                  ParkingScheduleResolver resolver,
                                  OperatorAssignmentPersistencePort assignmentPort,
                                  CurrentUserPort currentUserPort,
                                  Clock clock) {
      this.configPort = configPort;
      this.parkingPort = parkingPort;
      this.shiftPort = shiftPort;
      this.ratePort = ratePort;
      this.resolver = resolver;
      this.assignmentPort = assignmentPort;
      this.currentUserPort = currentUserPort;
      this.clock = clock;
   }

   // ========================= REEMPLAZAR TODO =========================

   @Override
   public List<ParkingShiftRateDto> configureShiftRates(ConfigureParkingShiftRatesCommand command) {
      Long parkingId = command.parkingId();
      ensureParkingExists(parkingId);
      List<ConfigureParkingShiftRatesCommand.Item> items =
            command.configurations() == null ? List.of() : command.configurations();
      if (items.size() > MAX_CONFIGURATIONS) {
         throw new ParkingDomainException("Un parqueo admite como máximo " + MAX_CONFIGURATIONS + " turnos");
      }

      Long userId = currentUserPort.requireCurrentUserId();
      Set<Long> seenShifts = new HashSet<>();
      List<Shift> activeShifts = new ArrayList<>();
      List<ParkingShiftRate> configs = new ArrayList<>();
      for (ConfigureParkingShiftRatesCommand.Item item : items) {
         if (item.shiftId() == null || item.rateId() == null) {
            throw new ParkingDomainException("Cada turno necesita su tarifa");
         }
         if (!seenShifts.add(item.shiftId())) {
            throw new ParkingDomainException("Un turno aparece más de una vez en la configuración");
         }
         boolean active = item.status() == null || item.status();
         Shift shift = loadShift(item.shiftId());
         Rate rate = loadRate(item.rateId());
         if (active) {
            ensureUsable(shift, rate);
            activeShifts.add(shift);
         }
         configs.add(ParkingShiftRate.create(parkingId, shift.getId(), rate.getId(), active, userId));
      }
      ParkingScheduleResolver.ensureNoOverlap(activeShifts);
      for (ParkingShiftRate previous : configPort.findByParkingId(parkingId)) {
         if (!seenShifts.contains(previous.getShiftId())) {
            ensureNoOperators(parkingId, previous.getShiftId());
         }
      }

      // Reemplazo completo: las transacciones guardan su propia tarifa, así que no se pierde historial
      configPort.deleteByParkingId(parkingId);
      List<ParkingShiftRate> saved = configPort.saveAll(configs);
      return toSortedDtos(saved);
   }

   // ========================= CONSULTAS =========================

   @Override
   @Transactional(readOnly = true)
   public List<ParkingShiftRateDto> getParkingShiftRates(Long parkingId) {
      ensureParkingExists(parkingId);
      return toSortedDtos(configPort.findByParkingId(parkingId));
   }

   @Override
   @Transactional(readOnly = true)
   public ParkingScheduleStatusDto getCurrentSchedule(Long parkingId) {
      ensureParkingExists(parkingId);
      LocalDateTime now = LocalDateTime.now(clock);
      return resolver.resolve(parkingId, now)
            .map(r -> new ParkingScheduleStatusDto(parkingId, now, true, toDto(r.config(), r.shift(), r.rate())))
            .orElseGet(() -> new ParkingScheduleStatusDto(parkingId, now, false, null));
   }

   // ========================= CAMBIOS PUNTUALES =========================

   @Override
   public ParkingShiftRateDto changeRate(Long configId, Long rateId) {
      ParkingShiftRate config = findConfig(configId);
      Rate rate = loadRate(rateId);
      if (config.isActive() && !rate.isUsable()) {
         throw new ResourceConflictException("La tarifa " + rate.getName() + " está inactiva");
      }
      config.changeRate(rate.getId());
      config.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(configPort.save(config));
   }

   @Override
   public void deleteShiftRateConfig(Long configId) {
      ParkingShiftRate config = findConfig(configId);
      ensureNoOperators(config.getParkingId(), config.getShiftId());
      configPort.deleteById(configId);
   }

   @Override
   public ParkingShiftRateDto toggleShiftRateStatus(Long configId) {
      ParkingShiftRate config = findConfig(configId);
      if (config.isActive()) {
         config.deactivate();
      } else {
         Shift shift = loadShift(config.getShiftId());
         ensureUsable(shift, loadRate(config.getRateId()));
         // Contra TODAS las activas, aunque su turno o tarifa estén inactivos ahora: si se
         // reactivan después, no deben aparecer solapes
         List<Shift> active = new ArrayList<>(configPort.findByParkingId(config.getParkingId()).stream()
               .filter(c -> c.isActive() && !c.getId().equals(configId))
               .map(c -> shiftPort.findById(c.getShiftId()))
               .flatMap(Optional::stream)
               .toList());
         active.add(shift);
         ParkingScheduleResolver.ensureNoOverlap(active);
         config.activate();
      }
      config.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(configPort.save(config));
   }

   // ========================= AUXILIARES =========================

   private void ensureParkingExists(Long parkingId) {
      parkingPort.findById(parkingId).orElseThrow(() -> new ResourceNotFoundException("Parqueo", parkingId));
   }

   /** La configuración debe ser de un parqueo vigente */
   private ParkingShiftRate findConfig(Long configId) {
      ParkingShiftRate config = configPort.findById(configId)
            .orElseThrow(() -> new ResourceNotFoundException("Configuración de turno", configId));
      ensureParkingExists(config.getParkingId());
      return config;
   }

   /**
    * Un turno con operadores asignados (vigentes o futuros) no se quita del horario del
    * parqueo: quedarían asignados a un horario en que el parqueo no existe. Desactivarlo sí
    * se permite (el parqueo deja de atender en ese turno).
    */
   private void ensureNoOperators(Long parkingId, Long shiftId) {
      long count = assignmentPort.countCurrentOrFutureByParkingAndShift(parkingId, shiftId, LocalDate.now(clock));
      if (count > 0) {
         String name = shiftPort.findByIdIncludingDeleted(shiftId).map(Shift::getName).orElse("turno " + shiftId);
         throw new ResourceConflictException("No se puede quitar el turno " + name + ": tiene " + count
               + (count == 1 ? " operador asignado" : " operadores asignados")
               + ". Finaliza esas asignaciones o desactiva el turno.");
      }
   }

   private Shift loadShift(Long shiftId) {
      return shiftPort.findById(shiftId).orElseThrow(() -> new ResourceNotFoundException("Turno", shiftId));
   }

   private Rate loadRate(Long rateId) {
      return ratePort.findById(rateId).orElseThrow(() -> new ResourceNotFoundException("Tarifa", rateId));
   }

   private static void ensureUsable(Shift shift, Rate rate) {
      if (!shift.isUsable()) {
         throw new ResourceConflictException("El turno " + shift.getName() + " está inactivo: actívalo o "
               + "guarda esta configuración como inactiva");
      }
      if (!rate.isUsable()) {
         throw new ResourceConflictException("La tarifa " + rate.getName() + " está inactiva: actívala o "
               + "elige otra");
      }
   }

   private List<ParkingShiftRateDto> toSortedDtos(List<ParkingShiftRate> configs) {
      return configs.stream()
            .map(this::toDto)
            .sorted(Comparator.comparing(ParkingShiftRateDto::startTime, Comparator.nullsLast(LocalTime::compareTo)))
            .toList();
   }

   private ParkingShiftRateDto toDto(ParkingShiftRate config) {
      // Incluye eliminados para que la pantalla muestre qué turno o tarifa quedó sin uso
      Shift shift = shiftPort.findByIdIncludingDeleted(config.getShiftId()).orElse(null);
      Rate rate = ratePort.findByIdIncludingDeleted(config.getRateId()).orElse(null);
      return toDto(config, shift, rate);
   }

   private ParkingShiftRateDto toDto(ParkingShiftRate c, Shift shift, Rate rate) {
      boolean shiftUsable = shift != null && shift.isUsable();
      boolean rateUsable = rate != null && rate.isUsable();
      return new ParkingShiftRateDto(
            c.getId(), c.getParkingId(),
            c.getShiftId(),
            shift != null ? shift.getName() : null,
            shift != null ? shift.getCode() : null,
            shift != null ? shift.getStartTime() : null,
            shift != null ? shift.getEndTime() : null,
            shift != null && shift.crossesMidnight(),
            shiftUsable,
            c.getRateId(),
            rate != null ? rate.getName() : null,
            rate != null ? rate.getAmount() : null,
            rate != null ? rate.getCurrency() : null,
            rateUsable,
            c.isActive(),
            c.isActive() && shiftUsable && rateUsable,
            c.getCreatedAt(), c.getCreatedBy(), c.getUpdatedAt(), c.getUpdatedBy()
      );
   }
}
