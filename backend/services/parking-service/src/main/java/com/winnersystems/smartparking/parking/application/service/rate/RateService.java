package com.winnersystems.smartparking.parking.application.service.rate;

import com.winnersystems.smartparking.parking.application.dto.command.CreateRateCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateRateCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.RateDto;
import com.winnersystems.smartparking.parking.application.dto.query.RateSimulationDto;
import com.winnersystems.smartparking.parking.application.port.input.rate.*;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingShiftRatePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.RatePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Rate;
import com.winnersystems.smartparking.parking.domain.model.Rate.BillingRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Casos de uso de las tarifas.
 *
 * Reglas:
 * - El nombre es único sin distinguir mayúsculas, también frente a las eliminadas (409).
 * - Una tarifa que usa algún parqueo no se elimina (409 con los parqueos); sí se desactiva.
 * - Una tarifa eliminada se restaura desde la papelera.
 * - La auditoría (createdBy, updatedBy, deletedBy) es el usuario del JWT.
 */
@Slf4j
@Service
@Transactional
public class RateService implements
      CreateRateUseCase,
      UpdateRateUseCase,
      DeleteRateUseCase,
      GetRateUseCase,
      ListRatesUseCase,
      ToggleRateStatusUseCase,
      RestoreRateUseCase,
      SimulateRateUseCase {

   private static final String RESOURCE = "Tarifa";

   private final RatePersistencePort ratePersistencePort;
   private final ParkingShiftRatePersistencePort parkingShiftRatePersistencePort;
   private final CurrentUserPort currentUserPort;

   public RateService(
         RatePersistencePort ratePersistencePort,
         ParkingShiftRatePersistencePort parkingShiftRatePersistencePort,
         CurrentUserPort currentUserPort) {
      this.ratePersistencePort = ratePersistencePort;
      this.parkingShiftRatePersistencePort = parkingShiftRatePersistencePort;
      this.currentUserPort = currentUserPort;
   }

   // ========================= CREATE =========================

   @Override
   public RateDto createRate(CreateRateCommand command) {
      Long userId = currentUserPort.requireCurrentUserId();

      // El dominio valida el nombre, el precio y las reglas de cobro
      Rate rate = Rate.create(
            command.name(),
            command.description(),
            command.amount(),
            new BillingRules(command.billingUnitMinutes(), command.minimumMinutes(),
                  command.graceMinutes(), command.toleranceMinutes()),
            command.status() == null || command.status(),
            userId);

      ensureNameIsAvailable(rate.getName(), null);

      Rate saved = ratePersistencePort.save(rate);
      log.info("Tarifa creada: id={}, usuario={}", saved.getId(), userId);
      return mapToDto(saved);
   }

   // ========================= UPDATE =========================

   @Override
   public RateDto updateRate(Long rateId, UpdateRateCommand command) {
      Rate rate = findRate(rateId);

      rate.update(
            command.name(),
            command.description(),
            command.amount(),
            new BillingRules(command.billingUnitMinutes(), command.minimumMinutes(),
                  command.graceMinutes(), command.toleranceMinutes()));
      ensureNameIsAvailable(rate.getName(), rateId);

      if (command.status() != null && command.status() != rate.isActive()) {
         changeStatus(rate, command.status());
      }

      rate.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return mapToDto(ratePersistencePort.save(rate));
   }

   // ========================= DELETE =========================

   @Override
   public void deleteRate(Long id) {
      Rate rate = findRate(id);

      // Una tarifa en uso no se elimina: el parqueo se quedaría sin precio en ese turno
      List<String> parkings = parkingShiftRatePersistencePort.findParkingNamesUsingRate(id);
      if (!parkings.isEmpty()) {
         throw new ResourceConflictException(
               "No se puede eliminar la tarifa " + rate.getName() + ": la usa " + describe(parkings)
                     + ". Cámbiala en esos parqueos o desactívala.");
      }

      rate.markAsDeleted(currentUserPort.requireCurrentUserId());
      ratePersistencePort.save(rate);
      log.info("Tarifa eliminada (soft delete): id={}", id);
   }

   // ========================= RESTORE =========================

   @Override
   public RateDto restoreRate(Long id) {
      Rate rate = ratePersistencePort.findDeletedById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tarifa eliminada no encontrada con ID: " + id));
      rate.restore(currentUserPort.requireCurrentUserId());
      Rate saved = ratePersistencePort.save(rate);
      log.info("Tarifa restaurada: id={}", id);
      return mapToDto(saved);
   }

   // ========================= SIMULACIÓN =========================

   @Override
   @Transactional(readOnly = true)
   public RateSimulationDto simulate(BigDecimal amount, BillingRules rules, List<Integer> durations) {
      // Tarifa temporal, validada como una real, que no se guarda
      Rate rate = Rate.create("Simulación", null, amount, rules, true, null);
      List<Integer> minutes = durations == null || durations.isEmpty() ? DEFAULT_DURATIONS : durations;
      return new RateSimulationDto(
            rate.getFractionAmount(),
            minutes.stream()
                  .distinct()
                  .sorted()
                  .map(m -> new RateSimulationDto.Row(m, rate.billableFractions(m), rate.calculateCost(m)))
                  .toList());
   }

   // ========================= GET Y LIST =========================

   @Override
   @Transactional(readOnly = true)
   public RateDto getRateById(Long id) {
      return mapToDto(findRate(id));
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<RateDto> listAllRates(int page, int size, String search, Boolean status, boolean deleted) {
      // La papelera muestra primero lo último que se eliminó
      PageRequest pageRequest = deleted
            ? PageRequest.of(page, size, "deletedAt", "DESC")
            : PageRequest.of(page, size, "id", "ASC");
      PageResult<Rate> result = ratePersistencePort.findAll(pageRequest, search, deleted ? null : status, deleted);
      return result.toPagedResponse(this::mapToDto);
   }

   @Override
   @Transactional(readOnly = true)
   public List<RateDto> listAllActiveRates() {
      return ratePersistencePort.findAllActive().stream()
            .map(this::mapToDto)
            .toList();
   }

   // ========================= ESTADO =========================

   @Override
   public RateDto toggleRateStatus(Long id) {
      Rate rate = findRate(id);
      return saveStatus(rate, !rate.isActive());
   }

   @Override
   public RateDto activateRate(Long id) {
      return saveStatus(findRate(id), true);
   }

   @Override
   public RateDto deactivateRate(Long id) {
      return saveStatus(findRate(id), false);
   }

   private RateDto saveStatus(Rate rate, boolean active) {
      changeStatus(rate, active);
      rate.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return mapToDto(ratePersistencePort.save(rate));
   }

   /** Desactivar una tarifa en uso está permitido; queda registrado en el log */
   private void changeStatus(Rate rate, boolean active) {
      if (active) {
         rate.activate();
         return;
      }
      List<String> parkings = parkingShiftRatePersistencePort.findParkingNamesUsingRate(rate.getId());
      if (!parkings.isEmpty()) {
         log.warn("Tarifa {} desactivada estando en uso por: {}", rate.getId(), parkings);
      }
      rate.deactivate();
   }

   // ========================= AUXILIARES =========================

   private Rate findRate(Long id) {
      return ratePersistencePort.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
   }

   /** @param currentId tarifa que se edita (puede conservar su propio nombre); null al crear */
   private void ensureNameIsAvailable(String name, Long currentId) {
      ratePersistencePort.findByNameIncludingDeleted(name)
            .filter(owner -> !owner.getId().equals(currentId))
            .ifPresent(owner -> {
               throw new ResourceConflictException(owner.isDeleted()
                     ? "El nombre " + name + " pertenece a una tarifa eliminada. Restáurala desde Eliminadas o usa otro nombre."
                     : "Ya existe una tarifa con el nombre " + name);
            });
   }

   /** "el parqueo San Martín" o "los parqueos San Martín y Las Banderas" */
   private static String describe(List<String> parkings) {
      if (parkings.size() == 1) {
         return "el parqueo " + parkings.get(0);
      }
      String last = parkings.get(parkings.size() - 1);
      return "los parqueos " + String.join(", ", parkings.subList(0, parkings.size() - 1)) + " y " + last;
   }

   private RateDto mapToDto(Rate rate) {
      List<String> usedBy = rate.getId() == null || rate.isDeleted()
            ? List.of()
            : parkingShiftRatePersistencePort.findParkingNamesUsingRate(rate.getId());
      return new RateDto(
            rate.getId(),
            rate.getName(),
            rate.getDescription(),
            rate.getAmount(),
            rate.getCurrency(),
            rate.getBillingUnitMinutes(),
            rate.getMinimumMinutes(),
            rate.getGraceMinutes(),
            rate.getToleranceMinutes(),
            rate.getFractionAmount(),
            rate.getStatus(),
            usedBy,
            rate.getCreatedAt(),
            rate.getCreatedBy(),
            rate.getUpdatedAt(),
            rate.getUpdatedBy(),
            rate.getDeletedAt(),
            rate.getDeletedBy()
      );
   }
}
