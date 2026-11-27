package com.winnersystems.smartparking.parking.application.service.infraction;

import com.winnersystems.smartparking.parking.application.dto.command.ChangeInfractionFineCommand;
import com.winnersystems.smartparking.parking.application.dto.command.EditInfractionCommand;
import com.winnersystems.smartparking.parking.application.dto.command.RegisterInfractionCommand;
import com.winnersystems.smartparking.parking.application.dto.query.*;
import com.winnersystems.smartparking.parking.application.port.input.infraction.ManageInfractionsUseCase;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.application.service.transaction.OperatorDutyChecker;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Infracciones: registro, consulta, edición de la multa y resolución.
 *
 * Reglas:
 * - Se identifica por la placa: el vehículo se busca o se crea.
 * - Zona del mismo parqueo y espacio de esa zona; la estadía (si se indica) del mismo parqueo y placa.
 * - Quien registra debe estar de turno en ese parqueo o zona (regla 8), salvo con
 *   transactions.any-parking.
 * - El cobro es de payment-service; aquí no se registran pagos.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 */
@Service
@Transactional
public class InfractionService implements ManageInfractionsUseCase {

   private static final Logger log = LoggerFactory.getLogger(InfractionService.class);

   private final InfractionPersistencePort infractionPort;
   private final ParkingPersistencePort parkingPort;
   private final ZonePersistencePort zonePort;
   private final SpacePersistencePort spacePort;
   private final TransactionPersistencePort transactionPort;
   private final VehiclePersistencePort vehiclePort;
   private final CustomerPersistencePort customerPort;
   private final UserDirectoryPort userDirectoryPort;
   private final CurrentUserPort currentUserPort;
   private final OperatorDutyChecker dutyChecker;
   private final Clock clock;
   private final int defaultDueDays;

   public InfractionService(InfractionPersistencePort infractionPort,
                            ParkingPersistencePort parkingPort,
                            ZonePersistencePort zonePort,
                            SpacePersistencePort spacePort,
                            TransactionPersistencePort transactionPort,
                            VehiclePersistencePort vehiclePort,
                            CustomerPersistencePort customerPort,
                            UserDirectoryPort userDirectoryPort,
                            CurrentUserPort currentUserPort,
                            OperatorDutyChecker dutyChecker,
                            Clock clock,
                            @Value("${parking.infractions.default-due-days:7}") int defaultDueDays) {
      this.infractionPort = infractionPort;
      this.parkingPort = parkingPort;
      this.zonePort = zonePort;
      this.spacePort = spacePort;
      this.transactionPort = transactionPort;
      this.vehiclePort = vehiclePort;
      this.customerPort = customerPort;
      this.userDirectoryPort = userDirectoryPort;
      this.currentUserPort = currentUserPort;
      this.dutyChecker = dutyChecker;
      this.clock = clock;
      this.defaultDueDays = defaultDueDays;
   }

   // ========================= REGISTRAR =========================

   @Override
   public InfractionDto register(RegisterInfractionCommand c) {
      LocalDateTime now = LocalDateTime.now(clock);
      Long userId = currentUserPort.requireCurrentUserId();
      String plate = PlateNumber.normalize(c.plateNumber());

      if (c.parkingId() == null) {
         throw new ParkingDomainException("El parqueo es obligatorio");
      }
      Parking parking = parkingPort.findById(c.parkingId())
            .orElseThrow(() -> new ResourceNotFoundException("Parqueo no encontrado: " + c.parkingId()));

      // La estadía relacionada fija la zona y el espacio si no se indicaron
      Transaction stay = null;
      Long zoneId = c.zoneId();
      Long spaceId = c.spaceId();
      if (c.transactionId() != null) {
         stay = transactionPort.findById(c.transactionId())
               .orElseThrow(() -> new ResourceNotFoundException("Estadía no encontrada: " + c.transactionId()));
         if (!stay.getParkingId().equals(parking.getId())) {
            throw new ResourceConflictException("La estadía es de otro parqueo");
         }
         String stayPlate = vehiclePort.findById(stay.getVehicleId()).map(Vehicle::getLicensePlate).orElse(null);
         if (stayPlate != null && !PlateNumber.normalize(stayPlate).equals(plate)) {
            throw new ResourceConflictException("La placa no coincide con la de la estadía (" + stayPlate + ")");
         }
         if (zoneId == null) {
            zoneId = stay.getZoneId();
            spaceId = spaceId != null ? spaceId : stay.getSpaceId();
         }
      }

      if (zoneId != null) {
         Zone zone = zonePort.findById(zoneId)
               .orElseThrow(() -> new ResourceNotFoundException("Zona no encontrada"));
         if (!zone.getParkingId().equals(parking.getId())) {
            throw new ResourceConflictException("La zona no pertenece al parqueo " + parking.getName());
         }
      }
      if (spaceId != null) {
         Space space = spacePort.findById(spaceId)
               .orElseThrow(() -> new ResourceNotFoundException("Espacio no encontrado"));
         if (!space.getZoneId().equals(zoneId)) {
            throw new ResourceConflictException("El espacio no pertenece a la zona indicada");
         }
      }

      // Regla 8: quien registra atiende ese parqueo o zona
      dutyChecker.ensureOnDuty(userId, parking, zoneId, now);

      // Vehículo por placa (se crea si es la primera vez; si algo falla después, se deshace todo)
      Vehicle vehicle = vehiclePort.findByPlateNumber(plate).orElseGet(() -> {
         Vehicle v = new Vehicle(plate);
         v.setCreatedBy(userId);
         return vehiclePort.save(v);
      });
      Infraction infraction = Infraction.register(new Infraction.RegisterData(parking.getId(), zoneId, spaceId,
            stay != null ? stay.getId() : null, vehicle.getId(), plate,
            stay != null ? stay.getCustomerId() : null, c.infractionType(), c.severity(), c.description(),
            c.evidence(), c.fineAmount(), c.fineDueDate(), c.detectedAt(), c.notes(), userId), now, defaultDueDays);
      infraction.setDetectedByName(userName(userId));

      Infraction saved = infractionPort.save(infraction);
      saved.generateInfractionCode();
      saved = infractionPort.save(saved);
      log.info("Infracción {} registrada en el parqueo {} para {}", saved.getInfractionCode(), parking.getId(), plate);
      return toDto(saved, new Lookups());
   }

   // ========================= CONSULTAS =========================

   @Override
   @Transactional(readOnly = true)
   public InfractionDto get(Long infractionId) {
      return toDto(load(infractionId), new Lookups());
   }

   @Override
   @Transactional(readOnly = true)
   public PageResult<InfractionDto> list(InfractionFilter filter, PageRequest pageRequest) {
      InfractionFilter f = filter != null ? filter : InfractionFilter.none();
      if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) {
         throw new ParkingDomainException("La fecha final no puede ser anterior a la inicial");
      }
      PageResult<Infraction> page = infractionPort.search(f, LocalDate.now(clock), pageRequest);
      Lookups lookups = new Lookups(page.content());
      return new PageResult<>(page.content().stream().map(i -> toDto(i, lookups)).toList(),
            page.pageNumber(), page.pageSize(), page.totalElements());
   }

   @Override
   @Transactional(readOnly = true)
   public List<InfractionDto> listByTransaction(Long transactionId) {
      List<Infraction> list = infractionPort.findByTransactionId(transactionId);
      Lookups lookups = new Lookups(list);
      return list.stream().map(i -> toDto(i, lookups)).toList();
   }

   @Override
   @Transactional(readOnly = true)
   public InfractionSummaryDto summary(Long parkingId) {
      return infractionPort.summary(parkingId, LocalDate.now(clock));
   }

   // ========================= EDICIÓN Y RESOLUCIÓN =========================

   @Override
   public InfractionDto edit(Long infractionId, EditInfractionCommand c) {
      Infraction i = load(infractionId);
      i.edit(new Infraction.EditData(c.infractionType(), c.severity(), c.description(), c.evidence(), c.notes()),
            currentUserPort.requireCurrentUserId(), LocalDateTime.now(clock));
      return toDto(infractionPort.save(i), new Lookups());
   }

   @Override
   public InfractionDto changeFine(Long infractionId, ChangeInfractionFineCommand c) {
      Infraction i = load(infractionId);
      i.changeFine(c.fineAmount(), c.dueDate(), currentUserPort.requireCurrentUserId(), LocalDateTime.now(clock),
            defaultDueDays);
      return toDto(infractionPort.save(i), new Lookups());
   }

   @Override
   public InfractionDto dismiss(Long infractionId, String reason) {
      Infraction i = load(infractionId);
      i.dismiss(reason, currentUserPort.requireCurrentUserId(), LocalDateTime.now(clock));
      log.info("Infracción {} anulada", i.getInfractionCode());
      return toDto(infractionPort.save(i), new Lookups());
   }

   @Override
   public InfractionDto closeWithWarning(Long infractionId, String comment) {
      Infraction i = load(infractionId);
      i.closeWithWarning(comment, currentUserPort.requireCurrentUserId(), LocalDateTime.now(clock));
      return toDto(infractionPort.save(i), new Lookups());
   }

   // ========================= APOYO =========================

   private Infraction load(Long id) {
      return infractionPort.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Infracción no encontrada: " + id));
   }

   /** Nombre de quien registra; si auth no responde o no hay permiso, se deja vacío */
   private String userName(Long userId) {
      try {
         return userDirectoryPort.findUser(userId).map(UserDirectoryPort.UserSummary::fullName).orElse(null);
      } catch (RuntimeException e) {
         log.debug("No se pudo obtener el nombre del usuario {}: {}", userId, e.getMessage());
         return null;
      }
   }

   /**
    * Parqueos, zonas, espacios y clientes de una página, consultados una sola vez.
    * Incluye eliminados: la infracción es historial y debe seguir mostrando sus nombres.
    */
   private final class Lookups {
      private final Map<Long, Parking> parkings;
      private final Map<Long, Zone> zones;
      private final Map<Long, Optional<Space>> spaces = new HashMap<>();
      private final Map<Long, Optional<Customer>> customers = new HashMap<>();

      Lookups() {
         this.parkings = new HashMap<>();
         this.zones = new HashMap<>();
      }

      Lookups(List<Infraction> list) {
         Set<Long> parkingIds = list.stream().map(Infraction::getParkingId).filter(Objects::nonNull)
               .collect(Collectors.toSet());
         Set<Long> zoneIds = list.stream().map(Infraction::getZoneId).filter(Objects::nonNull)
               .collect(Collectors.toSet());
         this.parkings = parkingIds.isEmpty() ? new HashMap<>() : parkingPort.findAllByIdsIncludingDeleted(parkingIds)
               .stream().collect(Collectors.toMap(Parking::getId, Function.identity(), (a, b) -> a, HashMap::new));
         this.zones = zoneIds.isEmpty() ? new HashMap<>() : zonePort.findAllByIdsIncludingDeleted(zoneIds)
               .stream().collect(Collectors.toMap(Zone::getId, Function.identity(), (a, b) -> a, HashMap::new));
      }

      Parking parking(Long id) {
         return id == null ? null : parkings.computeIfAbsent(id, k -> parkingPort.findByIdIncludingDeleted(k).orElse(null));
      }

      Zone zone(Long id) {
         return id == null ? null : zones.computeIfAbsent(id, k -> zonePort.findByIdIncludingDeleted(k).orElse(null));
      }

      Space space(Long id) {
         return id == null ? null : spaces.computeIfAbsent(id, spacePort::findByIdIncludingDeleted).orElse(null);
      }

      Customer customer(Long id) {
         return id == null ? null : customers.computeIfAbsent(id, customerPort::findById).orElse(null);
      }
   }

   private InfractionDto toDto(Infraction i, Lookups l) {
      LocalDate today = LocalDate.now(clock);
      Parking parking = l.parking(i.getParkingId());
      Zone zone = l.zone(i.getZoneId());
      Space space = l.space(i.getSpaceId());
      Customer customer = l.customer(i.getCustomerId());
      String plate = i.getPlateNumber() != null ? i.getPlateNumber()
            : vehiclePort.findById(i.getVehicleId()).map(Vehicle::getLicensePlate).orElse(null);
      return new InfractionDto(
            i.getId(), i.getInfractionCode(),
            i.getParkingId(), parking != null ? parking.getName() : null, parking != null ? parking.getCode() : null,
            i.getZoneId(), zone != null ? zone.getName() : null, zone != null ? zone.getCode() : null,
            i.getSpaceId(), space != null ? space.getCode() : null,
            i.getTransactionId(), i.getVehicleId(), plate,
            i.getCustomerId(), customer != null ? customer.getFullName() : null,
            i.getInfractionType(), i.getSeverity(), i.getDescription(), i.getEvidenceList(),
            i.getDetectedAt(), i.getDetectedBy(), i.getDetectedByName(), i.getDetectionMethod(),
            i.getFineAmount(), i.getCurrency(), i.getFineDueDate(),
            i.getFinePaidAmount(), i.getFineAmountDue(), i.isFinePaid(), i.getFinePaidAt(),
            i.getFinePaymentReference(),
            i.getStatus(), i.displayStatus(today), i.isFineOverdue(today), i.isFinePayable(), i.isEditable(),
            i.getResolutionType(), i.getResolution(), i.getResolvedAt(), i.getResolvedBy(),
            i.getNotes(),
            i.getCreatedAt(), i.getCreatedBy(), i.getUpdatedAt(), i.getUpdatedBy());
   }
}
