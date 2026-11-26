package com.winnersystems.smartparking.parking.application.service.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.CancelTransactionCommand;
import com.winnersystems.smartparking.parking.application.dto.command.ReassignSpaceCommand;
import com.winnersystems.smartparking.parking.application.dto.command.RecordEntryCommand;
import com.winnersystems.smartparking.parking.application.dto.command.RecordExitCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateTransactionCommand;
import com.winnersystems.smartparking.parking.application.dto.query.*;
import com.winnersystems.smartparking.parking.application.port.input.transaction.*;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.application.service.parkingshiftrate.ParkingScheduleResolver;
import com.winnersystems.smartparking.parking.domain.exception.*;
import com.winnersystems.smartparking.parking.domain.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Servicio de aplicación de las estadías: entrada, salida, cambio de espacio, cancelación y consultas.
 *
 * Hay tres formas de entrar o salir y todas pasan por enterVehicle / exitVehicle, para que las
 * reglas se escriban una sola vez:
 * - el operador desde el panel (recordEntry / recordExit, método MANUAL);
 * - la cámara, vía iot-service (CameraCaptureService, método CAMERA_AI);
 * - la confirmación de una lectura dudosa (PlateReviewService).
 *
 * El pago lo cobra payment-service y lo avisa por /internal/charges (ChargeService).
 * La salida exige que no se deba nada: tras pagar hay parking.payment.exit-window-minutes para salir.
 */
@Service
@Transactional
public class TransactionService implements
      RecordEntryUseCase,
      RecordExitUseCase,
      GetTransactionUseCase,
      ListActiveTransactionsUseCase,
      ListTransactionsUseCase,
      CancelTransactionUseCase,
      UpdateTransactionUseCase,
      ReassignSpaceUseCase {

   // ========================= PUERTOS DE SALIDA =========================

   private final TransactionPersistencePort transactionPersistencePort;
   private final VehiclePersistencePort vehiclePersistencePort;
   private final CustomerPersistencePort customerPersistencePort;
   private final CustomerVehiclePersistencePort customerVehiclePersistencePort;
   private final ParkingPersistencePort parkingPersistencePort;
   private final SpacePersistencePort spacePersistencePort;
   private final ZonePersistencePort zonePersistencePort;
   private final RatePersistencePort ratePersistencePort;
   private final ParkingScheduleResolver scheduleResolver;
   private final DocumentTypePersistencePort documentTypePersistencePort;
   private final CurrentUserPort currentUserPort;
   private final OperatorDutyChecker dutyChecker;
   private final RandomGenerator random;
   private final Clock clock;
   private final int exitWindowMinutes;

   // ========================= CONSTANTES =========================

   private static final int MAX_RECOMMENDED_MINUTES = 480; // 8 horas

   // ========================= CONSTRUCTOR =========================

   public TransactionService(
         TransactionPersistencePort transactionPersistencePort,
         VehiclePersistencePort vehiclePersistencePort,
         CustomerPersistencePort customerPersistencePort,
         CustomerVehiclePersistencePort customerVehiclePersistencePort,
         ParkingPersistencePort parkingPersistencePort,
         SpacePersistencePort spacePersistencePort,
         ZonePersistencePort zonePersistencePort,
         RatePersistencePort ratePersistencePort,
         ParkingScheduleResolver scheduleResolver,
         DocumentTypePersistencePort documentTypePersistencePort,
         CurrentUserPort currentUserPort,
         OperatorDutyChecker dutyChecker,
         RandomGenerator random,
         Clock clock,
         @Value("${parking.payment.exit-window-minutes:15}") int exitWindowMinutes) {
      this.transactionPersistencePort = transactionPersistencePort;
      this.vehiclePersistencePort = vehiclePersistencePort;
      this.customerPersistencePort = customerPersistencePort;
      this.customerVehiclePersistencePort = customerVehiclePersistencePort;
      this.parkingPersistencePort = parkingPersistencePort;
      this.spacePersistencePort = spacePersistencePort;
      this.zonePersistencePort = zonePersistencePort;
      this.ratePersistencePort = ratePersistencePort;
      this.scheduleResolver = scheduleResolver;
      this.documentTypePersistencePort = documentTypePersistencePort;
      this.currentUserPort = currentUserPort;
      this.dutyChecker = dutyChecker;
      this.random = random;
      this.clock = clock;
      this.exitWindowMinutes = exitWindowMinutes;
   }

   // ========================= ENTRADA Y SALIDA MANUAL =========================

   /** Entrada desde el panel: el operador sale del token y el método es MANUAL */
   @Override
   public TransactionDetailDto recordEntry(RecordEntryCommand command) {
      return enterVehicle(new EntryRequest(
            command.plateNumber(), command.parkingId(), command.zoneId(), command.spaceId(),
            command.documentTypeId(), command.documentNumber(),
            command.customerFirstName(), command.customerLastName(),
            command.customerPhone(), command.customerEmail(),
            Transaction.METHOD_MANUAL, currentUserPort.requireCurrentUserId(),
            null, null, null, command.notes()));
   }

   /** Salida desde el panel: el operador sale del token y el método es MANUAL */
   @Override
   public TransactionDetailDto recordExit(RecordExitCommand command) {
      return exitVehicle(new ExitRequest(
            command.transactionId(), command.plateNumber(), null,
            command.exitDocumentTypeId(), command.exitDocumentNumber(),
            Transaction.METHOD_MANUAL, currentUserPort.requireCurrentUserId(),
            null, null, null, command.notes()));
   }

   // ========================= NÚCLEO DE ENTRADA =========================

   /**
    * Registra una entrada (manual, por cámara o al confirmar una revisión).
    *
    * Orden: primero todo lo que puede fallar sin escribir nada (parqueo, zona, turno, tarifa,
    * vehículo dentro, espacio), después se guarda.
    */
   public TransactionDetailDto enterVehicle(EntryRequest request) {
      LocalDateTime now = LocalDateTime.now(clock);
      String plate = PlateNumber.normalize(request.plateNumber());

      // 1. Parqueo operativo y su política de documento
      Parking parking = loadParking(request.parkingId());
      validateParkingOperational(parking);
      boolean hasDocument = request.documentTypeId() != null
            || (request.documentNumber() != null && !request.documentNumber().isBlank());
      parking.ensureEntryDocument(hasDocument);

      // 2. Zona operativa y del parqueo
      Zone zone = loadZone(request.zoneId());
      validateZoneOperational(zone);
      if (!zone.belongsTo(parking.getId())) {
         throw new ParkingDomainException("La zona " + zone.getName() + " no pertenece al parqueo " + parking.getName());
      }

      // 3. Quien registra está de turno allí (regla 8)
      if (request.operatorId() != null) {
         dutyChecker.ensureOnDuty(request.operatorId(), parking, zone.getId(), now);
      }

      // 4. El parqueo atiende a esta hora: tarifa del turno vigente (regla 1)
      Rate rate = findApplicableRate(parking, now);

      // 5. El vehículo no está dentro
      Vehicle vehicle = vehiclePersistencePort.findByPlateNumber(plate).orElse(null);
      if (vehicle != null) {
         validateVehicleNotInside(vehicle);
      }

      // 6. Espacio: el indicado o uno libre al azar (libre elección del conductor)
      Space space = resolveEntrySpace(request.spaceId(), zone);

      // 7. Documento normalizado (si se dio) antes de crear nada
      String document = hasDocument ? validDocumentNumber(request.documentTypeId(), request.documentNumber()) : null;

      // 8. Guardar: vehículo, cliente (solo con documento), estadía y espacio ocupado
      if (vehicle == null) {
         vehicle = new Vehicle(plate);
         vehicle.setCreatedBy(request.operatorId());
         vehicle = vehiclePersistencePort.save(vehicle);
      }
      Customer customer = null;
      if (hasDocument) {
         customer = findOrCreateCustomer(request, document);
         registerCustomerVehicleRelation(customer.getId(), vehicle.getId());
      }

      Transaction transaction = Transaction.registerEntry(new Transaction.EntryData(
            vehicle.getId(), customer != null ? customer.getId() : null,
            parking.getId(), zone.getId(), space.getId(), rate.getId(),
            hasDocument ? request.documentTypeId() : null, document,
            request.method(), request.operatorId(),
            request.captureId(), request.photoUrl(), request.plateConfidence()), now);
      if (request.notes() != null && !request.notes().isBlank()) {
         transaction.setNotes(request.notes().trim());
      }
      Transaction saved = transactionPersistencePort.save(transaction);

      space.occupy();
      spacePersistencePort.save(space);

      vehicle.recordVisit();
      vehiclePersistencePort.save(vehicle);
      if (customer != null) {
         customer.recordVisit();
         customerPersistencePort.save(customer);
      }

      return buildTransactionDetailDto(saved, vehicle, customer, parking, zone, space, rate);
   }

   // ========================= NÚCLEO DE SALIDA =========================

   /**
    * Registra una salida (manual, por cámara o al confirmar una revisión).
    *
    * @throws PaymentRequiredException  si falta pagar (409): la estadía no cambia
    * @throws DocumentMismatchException si el parqueo exige documento y no coincide (403)
    */
   public TransactionDetailDto exitVehicle(ExitRequest request) {
      LocalDateTime now = LocalDateTime.now(clock);
      Transaction transaction = findActiveTransaction(request.transactionId(), request.plateNumber());

      if (request.expectedParkingId() != null && !request.expectedParkingId().equals(transaction.getParkingId())) {
         throw new ResourceConflictException("La estadía de esta placa es de otro parqueo");
      }

      Parking parking = loadParking(transaction.getParkingId());
      Zone zone = loadZone(transaction.getZoneId());
      Space space = loadSpace(transaction.getSpaceId());
      Rate rate = loadRate(transaction.getRateId());

      if (request.operatorId() != null) {
         dutyChecker.ensureOnDuty(request.operatorId(), parking, zone.getId(), now);
      }

      boolean hasDocument = request.documentTypeId() != null
            || (request.documentNumber() != null && !request.documentNumber().isBlank());
      String document = hasDocument ? validDocumentNumber(request.documentTypeId(), request.documentNumber()) : null;

      transaction.completeExit(new Transaction.ExitData(
                  hasDocument ? request.documentTypeId() : null, document,
                  request.method(), request.operatorId(),
                  request.captureId(), request.photoUrl(), request.plateConfidence()),
            rate, now, exitWindowMinutes, parking.isDriverDocumentRequired());
      appendNotes(transaction, request.notes());

      Transaction saved = transactionPersistencePort.save(transaction);

      space.release();
      spacePersistencePort.save(space);

      return buildTransactionDetailDto(saved, loadVehicle(saved.getVehicleId()), loadCustomerOrNull(saved),
            parking, zone, space, rate);
   }

   /** Lo que falta pagar para salir ahora (lo usa la cámara de salida para mostrar el monto) */
   @Transactional(readOnly = true)
   public BigDecimal amountDueNow(Long transactionId) {
      Transaction transaction = loadTransaction(transactionId);
      if (!transaction.isActive()) {
         return BigDecimal.ZERO;
      }
      return transaction.amountDueAt(LocalDateTime.now(clock), loadRate(transaction.getRateId()), exitWindowMinutes);
   }

   // ========================= CAMBIO DE ESPACIO =========================

   @Override
   public TransactionDetailDto reassignSpace(Long transactionId, ReassignSpaceCommand command) {
      if (command == null || command.spaceId() == null) {
         throw new IllegalArgumentException("Indica el espacio nuevo");
      }
      Transaction transaction = loadTransaction(transactionId);
      Space newSpace = spacePersistencePort.findById(command.spaceId())
            .orElseThrow(() -> new ResourceNotFoundException("Espacio no encontrado: " + command.spaceId()));
      if (!newSpace.belongsTo(transaction.getZoneId())) {
         throw new ResourceConflictException("El espacio " + newSpace.getCode() + " es de otra zona");
      }
      validateSpaceAvailable(newSpace);

      Long userId = currentUserPort.requireCurrentUserId();
      Long previousId = transaction.reassignSpace(newSpace.getId(), userId);
      Transaction saved = transactionPersistencePort.save(transaction);

      spacePersistencePort.findByIdIncludingDeleted(previousId).ifPresent(previous -> {
         if (previous.isOccupied()) {
            previous.release();
            spacePersistencePort.save(previous);
         }
      });
      newSpace.occupy();
      spacePersistencePort.save(newSpace);

      return loadTransactionDetailDto(saved);
   }

   // ========================= CANCELACIÓN =========================

   /** Estadía registrada por error: solo en curso y sin pagos; libera el espacio */
   @Override
   public TransactionDto cancelTransaction(Long transactionId, CancelTransactionCommand command) {
      Transaction transaction = loadTransaction(transactionId);
      transaction.cancel(command.reason(), currentUserPort.requireCurrentUserId());
      Transaction saved = transactionPersistencePort.save(transaction);

      spacePersistencePort.findByIdIncludingDeleted(saved.getSpaceId()).ifPresent(space -> {
         if (space.isOccupied()) {
            space.release();
            spacePersistencePort.save(space);
         }
      });
      return buildTransactionDto(saved);
   }

   // ========================= OBSERVACIONES =========================

   /** Cambia las observaciones (se permite también en estadías cerradas: es una nota) */
   @Override
   public TransactionDto updateTransaction(Long transactionId, UpdateTransactionCommand command) {
      Transaction transaction = loadTransaction(transactionId);
      String notes = command == null || command.notes() == null || command.notes().isBlank()
            ? null : command.notes().trim();
      if (notes != null && notes.length() > 500) {
         throw new ParkingDomainException("Las observaciones no pueden superar 500 caracteres");
      }
      transaction.setNotes(notes);
      transaction.setUpdatedBy(currentUserPort.requireCurrentUserId());
      transaction.setUpdatedAt(LocalDateTime.now(clock));
      return buildTransactionDto(transactionPersistencePort.save(transaction));
   }

   // ========================= GetTransactionUseCase =========================

   @Override
   @Transactional(readOnly = true)
   public TransactionDetailDto getTransactionById(Long transactionId) {
      Transaction transaction = loadTransaction(transactionId);
      return loadTransactionDetailDto(transaction);
   }

   @Override
   @Transactional(readOnly = true)
   public TransactionDetailDto getActiveTransactionByPlate(String plateNumber) {
      if (!PlateNumber.isValid(plateNumber)) {
         return null;
      }
      return transactionPersistencePort.findActiveByPlateNumber(PlateNumber.normalize(plateNumber))
            .map(this::loadTransactionDetailDto)
            .orElse(null);
   }

   // ========================= ListActiveTransactionsUseCase =========================

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<ActiveTransactionDto> listAllActiveTransactions(int pageNumber, int pageSize) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, "entryTime", "DESC");
      PageResult<Transaction> result = transactionPersistencePort.findAllActive(request);
      return toActiveTransactionPagedResponse(result);
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<ActiveTransactionDto> listActiveTransactions(Long parkingId, Long zoneId, String plate,
                                                                     int pageNumber, int pageSize) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, "entryTime", "DESC");
      return toActiveTransactionPagedResponse(
            transactionPersistencePort.findActiveFiltered(parkingId, zoneId, plate, request));
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<ActiveTransactionDto> listActiveTransactionsByZone(Long zoneId, int pageNumber, int pageSize) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, "entryTime", "DESC");
      PageResult<Transaction> result = transactionPersistencePort.findActiveByZoneId(zoneId, request);
      return toActiveTransactionPagedResponse(result);
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<ActiveTransactionDto> searchActiveTransactionsByPlate(String plateNumber, int pageNumber, int pageSize) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, "entryTime", "DESC");
      PageResult<Transaction> result = transactionPersistencePort.searchActiveByPlate(plateNumber, request);
      return toActiveTransactionPagedResponse(result);
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<ActiveTransactionDto> listOverdueTransactions(int pageNumber, int pageSize) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, "entryTime", "ASC");
      PageResult<Transaction> result = transactionPersistencePort.findOverdue(MAX_RECOMMENDED_MINUTES, request);
      return toActiveTransactionPagedResponse(result);
   }

   // ========================= ListTransactionsUseCase =========================

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<TransactionDto> listAllTransactions(int pageNumber, int pageSize, String sortBy, String sortDirection) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, sortBy, sortDirection);
      PageResult<Transaction> result = transactionPersistencePort.findAll(request);
      return toTransactionPagedResponse(result);
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<TransactionDto> listTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate,
                                                                    int pageNumber, int pageSize) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, "entryTime", "DESC");
      PageResult<Transaction> result = transactionPersistencePort.findByDateRange(startDate, endDate, request);
      return toTransactionPagedResponse(result);
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<TransactionDto> listTransactionsByStatus(String status, int pageNumber, int pageSize) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, "createdAt", "DESC");
      PageResult<Transaction> result = transactionPersistencePort.findByStatus(status, request);
      return toTransactionPagedResponse(result);
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<TransactionDto> listTransactionsByPaymentStatus(String paymentStatus, int pageNumber, int pageSize) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, "createdAt", "DESC");
      PageResult<Transaction> result = transactionPersistencePort.findByPaymentStatus(paymentStatus, request);
      return toTransactionPagedResponse(result);
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<TransactionDto> listTransactionsByZone(Long zoneId, int pageNumber, int pageSize) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, "entryTime", "DESC");
      PageResult<Transaction> result = transactionPersistencePort.findByZoneId(zoneId, request);
      return toTransactionPagedResponse(result);
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<TransactionDto> searchTransactionsByPlate(String plateNumber, int pageNumber, int pageSize) {
      PageRequest request = PageRequest.of(pageNumber, pageSize, "entryTime", "DESC");
      PageResult<Transaction> result = transactionPersistencePort.searchByPlate(plateNumber, request);
      return toTransactionPagedResponse(result);
   }

   // ========================= HELPERS - CARGA DE ENTIDADES =========================

   /**
    * Incluye eliminados para mostrar el historial. Una entrada nueva igual se
    * rechaza: validateParkingOperational exige un parqueo activo y no eliminado.
    */
   private Parking loadParking(Long parkingId) {
      return parkingPersistencePort.findByIdIncludingDeleted(parkingId)
            .orElseThrow(() -> new ResourceNotFoundException("Parqueo no encontrado: " + parkingId));
   }

   private Transaction loadTransaction(Long transactionId) {
      return transactionPersistencePort.findById(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("Estadía no encontrada: " + transactionId));
   }

   /** Incluye eliminadas para el historial; una entrada nueva exige una zona operativa */
   private Zone loadZone(Long zoneId) {
      return zonePersistencePort.findByIdIncludingDeleted(zoneId)
            .orElseThrow(() -> new ResourceNotFoundException("Zona no encontrada: " + zoneId));
   }

   /** Incluye eliminados para el historial; una entrada nueva exige un espacio libre y vigente */
   private Space loadSpace(Long spaceId) {
      return spacePersistencePort.findByIdIncludingDeleted(spaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Espacio no encontrado: " + spaceId));
   }

   private Vehicle loadVehicle(Long vehicleId) {
      return vehiclePersistencePort.findById(vehicleId)
            .orElseThrow(() -> new ResourceNotFoundException("Vehículo no encontrado: " + vehicleId));
   }

   /** Sin documento no hay cliente: la estadía queda ligada solo a la placa */
   private Customer loadCustomerOrNull(Transaction transaction) {
      return transaction.getCustomerId() == null ? null
            : customerPersistencePort.findById(transaction.getCustomerId()).orElse(null);
   }

   /** Incluye eliminadas: una transacción conserva su tarifa aunque luego se elimine */
   private Rate loadRate(Long rateId) {
      return ratePersistencePort.findByIdIncludingDeleted(rateId)
            .orElseThrow(() -> new ResourceNotFoundException("Tarifa no encontrada: " + rateId));
   }

   // ========================= HELPERS - VALIDACIONES =========================

   private void validateParkingOperational(Parking parking) {
      if (!parking.isOperational()) {
         throw new ParkingNotOperationalException(parking.getName(), parking.isDeleted() ? "ELIMINADO" : parking.getStatus());
      }
   }

   private void validateZoneOperational(Zone zone) {
      if (!zone.isOperational()) {
         throw new ZoneNotOperationalException(zone.getName(), "La zona no está operativa");
      }
   }

   private void validateSpaceAvailable(Space space) {
      if (!space.isAvailableForOccupation()) {
         throw new SpaceNotAvailableException(space.getCode(), space.isDeleted() ? "ELIMINADO" : space.getStatus());
      }
   }

   private void validateVehicleNotInside(Vehicle vehicle) {
      transactionPersistencePort.findActiveByVehicleId(vehicle.getId()).ifPresent(active -> {
         throw new VehicleAlreadyInsideException(vehicle.getLicensePlate(), active.getId());
      });
   }

   /**
    * El espacio indicado (libre y de la zona) o uno libre al azar. El conductor estaciona donde
    * quiera: este espacio solo hace que la disponibilidad sea exacta desde la entrada.
    */
   private Space resolveEntrySpace(Long spaceId, Zone zone) {
      if (spaceId != null) {
         Space space = spacePersistencePort.findById(spaceId)
               .orElseThrow(() -> new ResourceNotFoundException("Espacio no encontrado: " + spaceId));
         if (!space.belongsTo(zone.getId())) {
            throw new ParkingDomainException("El espacio " + space.getCode() + " no pertenece a la zona " + zone.getName());
         }
         validateSpaceAvailable(space);
         return space;
      }
      return SpaceAllocator.pickRandomAvailable(spacePersistencePort.findAvailableByZoneId(zone.getId()),
                  zone.getId(), random)
            .orElseThrow(() -> new ResourceConflictException("La zona " + zone.getName() + " está llena"));
   }

   /**
    * El tipo de documento debe estar activo y el número cumplir su formato (longitud, solo dígitos).
    * @return el número normalizado (sin espacios ni guiones, en mayúsculas)
    */
   private String validDocumentNumber(Long documentTypeId, String documentNumber) {
      if (documentTypeId == null || documentNumber == null || documentNumber.isBlank()) {
         throw new ParkingDomainException("El tipo y el número de documento se indican juntos");
      }
      DocumentType type = documentTypePersistencePort.findById(documentTypeId)
            .filter(DocumentType::isUsable)
            .orElseThrow(() -> new ParkingDomainException("Tipo de documento no disponible"));
      return type.normalizeNumber(documentNumber);
   }

   // ========================= HELPERS - BUSCAR O CREAR =========================

   private Customer findOrCreateCustomer(EntryRequest request, String normalizedDocument) {
      return customerPersistencePort.findByDocument(request.documentTypeId(), normalizedDocument)
            .orElseGet(() -> {
               Customer customer = new Customer(
                     request.documentTypeId(),
                     normalizedDocument,
                     request.customerFirstName(),
                     request.customerLastName()
               );
               if (request.customerPhone() != null && !request.customerPhone().isBlank()) {
                  customer.setPhone(request.customerPhone().trim());
               }
               if (request.customerEmail() != null && !request.customerEmail().isBlank()) {
                  customer.setEmail(request.customerEmail().trim().toLowerCase());
               }
               customer.setCreatedBy(request.operatorId());
               return customerPersistencePort.save(customer);
            });
   }

   private void registerCustomerVehicleRelation(Long customerId, Long vehicleId) {
      customerVehiclePersistencePort.findByCustomerAndVehicle(customerId, vehicleId)
            .ifPresentOrElse(
                  relation -> {
                     relation.incrementUsage();
                     customerVehiclePersistencePort.save(relation);
                  },
                  () -> {
                     CustomerVehicle relation = new CustomerVehicle(customerId, vehicleId);
                     relation.incrementUsage(); // Inicializa en 1
                     customerVehiclePersistencePort.save(relation);
                  }
            );
   }

   // ========================= HELPERS - TRANSACCIONES =========================

   private Transaction findActiveTransaction(Long transactionId, String plateNumber) {
      if (transactionId != null) {
         Transaction transaction = loadTransaction(transactionId);
         if (!transaction.isActive()) {
            throw new InvalidTransactionStateException(transaction.getId(), transaction.getStatus(),
                  Transaction.STATUS_ACTIVE, "registrar salida");
         }
         return transaction;
      }
      if (plateNumber == null || plateNumber.isBlank()) {
         throw new IllegalArgumentException("Indica la estadía o la placa");
      }
      String plate = PlateNumber.normalize(plateNumber);
      return transactionPersistencePort.findActiveByPlateNumber(plate)
            .orElseThrow(() -> new ResourceNotFoundException("No hay una estadía en curso para la placa " + plate));
   }

   private void appendNotes(Transaction transaction, String notes) {
      if (notes == null || notes.isBlank()) {
         return;
      }
      String existing = transaction.getNotes();
      transaction.setNotes(existing != null ? existing + " | " + notes.trim() : notes.trim());
   }

   // ========================= HELPERS - TARIFAS =========================

   /**
    * Tarifa del turno vigente del parqueo (ParkingShiftRate). Si a esta hora no hay un turno
    * activo con tarifa activa, el parqueo no atiende (regla de negocio 1): 409.
    */
   private Rate findApplicableRate(Parking parking, LocalDateTime at) {
      return scheduleResolver.resolve(parking.getId(), at)
            .map(ParkingScheduleResolver.Resolved::rate)
            .orElseThrow(() -> new ResourceConflictException("El parqueo " + parking.getName()
                  + " no atiende a esta hora: no tiene un turno activo con tarifa para las "
                  + at.toLocalTime().withSecond(0).withNano(0) + ". Revisa sus horarios y tarifas."));
   }

   // ========================= BUILDERS - DTOs =========================

   private String documentTypeCode(Long documentTypeId) {
      if (documentTypeId == null) {
         return null;
      }
      return documentTypePersistencePort.findByIdIncludingDeleted(documentTypeId)
            .map(DocumentType::getCode)
            .orElse(null);
   }

   /** Detalle completo de una estadía. El cliente puede ser null (entró solo con la placa) */
   private TransactionDetailDto buildTransactionDetailDto(Transaction t, Vehicle v, Customer c,
                                                          Parking p, Zone z, Space sp, Rate r) {
      return new TransactionDetailDto(
            t.getId(),
            t.getStatus(),
            t.getPaymentStatus(),

            new TransactionDetailDto.VehicleInfo(v.getId(), v.getLicensePlate()),

            c == null ? null : new TransactionDetailDto.CustomerInfo(
                  c.getId(),
                  documentTypeCode(c.getDocumentTypeId()),
                  c.getDocumentNumber(),
                  c.getFullName(),
                  c.getPhone(),
                  c.getEmail()
            ),

            new TransactionDetailDto.ParkingInfo(p.getId(), p.getName(), p.getCode(),
                  p.isDriverDocumentRequired()),
            new TransactionDetailDto.ZoneInfo(z.getId(), z.getName(), z.getCode()),
            new TransactionDetailDto.SpaceInfo(sp.getId(), sp.getCode(), sp.getType()),

            // Tiempos
            t.getEntryTime(),
            t.getExitTime(),
            t.getDurationMinutes(),
            t.getFormattedDuration(),

            // Documentos (opcionales)
            t.getEntryDocumentNumber() != null
                  ? new TransactionDetailDto.DocumentInfo(documentTypeCode(t.getEntryDocumentTypeId()), t.getEntryDocumentNumber())
                  : null,
            t.getExitDocumentNumber() != null
                  ? new TransactionDetailDto.DocumentInfo(documentTypeCode(t.getExitDocumentTypeId()), t.getExitDocumentNumber())
                  : null,

            new TransactionDetailDto.RateInfo(r.getId(), r.getName(), r.getAmount()),

            // Montos
            t.getCalculatedAmount(),
            t.getDiscountAmount(),
            t.getTotalAmount(),
            t.getCurrency(),

            // Pago: lo cobra payment-service; aquí, cuánto se pagó y cuánto falta para salir
            buildPaymentInfo(t, r),

            // Operadores (null si la cámara registró sola)
            t.getEntryOperatorId() != null ? new TransactionDetailDto.OperatorInfo(t.getEntryOperatorId(), null, null) : null,
            t.getExitOperatorId() != null ? new TransactionDetailDto.OperatorInfo(t.getExitOperatorId(), null, null) : null,

            // Evidencia
            t.getEntryPhotoUrl(),
            t.getExitPhotoUrl(),
            t.getEntryPlateConfidence(),
            t.getExitPlateConfidence(),

            // Comprobante
            t.getReceiptSent(),
            t.getReceiptSentAt(),
            t.getReceiptWhatsAppStatus(),
            t.getReceiptEmailStatus(),

            // Observaciones
            t.getNotes(),
            t.getCancellationReason(),

            // Auditoría
            t.getCreatedAt(),
            t.getUpdatedAt(),

            t.getEntryMethod(),
            t.getExitMethod()
      );
   }

   private TransactionDetailDto loadTransactionDetailDto(Transaction t) {
      return buildTransactionDetailDto(t,
            loadVehicle(t.getVehicleId()),
            loadCustomerOrNull(t),
            loadParking(t.getParkingId()),
            loadZone(t.getZoneId()),
            loadSpace(t.getSpaceId()),
            loadRate(t.getRateId()));
   }

   /** Pagado, pendiente y hasta cuándo puede salir sin pagar más */
   private TransactionDetailDto.PaymentInfo buildPaymentInfo(Transaction t, Rate rate) {
      BigDecimal due = t.isActive()
            ? t.amountDueAt(LocalDateTime.now(clock), rate, exitWindowMinutes)
            : BigDecimal.ZERO;
      LocalDateTime exitDeadline = t.isActive() && t.getPaidAt() != null && due.signum() == 0
            ? t.getPaidAt().plusMinutes(exitWindowMinutes)
            : null;
      return new TransactionDetailDto.PaymentInfo(t.getPaidAmount(), t.getPaidAt(), due, exitDeadline);
   }

   /** Estadía en curso para el monitoreo en vivo */
   private ActiveTransactionDto buildActiveTransactionDto(Transaction t) {
      Vehicle vehicle = loadVehicle(t.getVehicleId());
      Customer customer = loadCustomerOrNull(t);
      Parking parking = loadParking(t.getParkingId());
      Zone zone = loadZone(t.getZoneId());
      Space space = loadSpace(t.getSpaceId());
      Rate rate = loadRate(t.getRateId());

      LocalDateTime now = LocalDateTime.now(clock);
      int elapsedMinutes = (int) Duration.between(t.getEntryTime(), now).toMinutes();
      // Lo que falta pagar si saliera ahora (descuenta lo ya pagado y respeta el tiempo para salir)
      BigDecimal currentAmount = t.amountDueAt(now, rate, exitWindowMinutes);
      boolean isOverdue = elapsedMinutes > MAX_RECOMMENDED_MINUTES;

      return ActiveTransactionDto.of(
            t.getId(),
            vehicle.getId(), vehicle.getLicensePlate(),
            customer != null ? customer.getId() : null,
            customer != null ? customer.getFullName() : null,
            customer != null ? customer.getPhone() : null,
            customer != null ? customer.getEmail() : null,
            documentTypeCode(t.getEntryDocumentTypeId()), t.getEntryDocumentNumber(),
            parking.getId(), parking.getName(),
            zone.getId(), zone.getName(),
            space.getId(), space.getCode(),
            t.getEntryTime(), elapsedMinutes, formatDuration(elapsedMinutes),
            rate.getAmount(), currentAmount,
            isOverdue, MAX_RECOMMENDED_MINUTES, isOverdue,
            t.getEntryOperatorId(), null, t.getEntryMethod(),
            t.getEntryPhotoUrl(), t.getEntryPlateConfidence(),
            t.getNotes(), t.getCreatedAt()
      );
   }

   /** Estadía resumida para listados */
   private TransactionDto buildTransactionDto(Transaction t) {
      Vehicle vehicle = loadVehicle(t.getVehicleId());
      Customer customer = loadCustomerOrNull(t);
      Parking parking = loadParking(t.getParkingId());
      Zone zone = loadZone(t.getZoneId());
      Space space = loadSpace(t.getSpaceId());

      return new TransactionDto(
            t.getId(),
            vehicle.getLicensePlate(),
            customer != null ? customer.getFullName() : null,
            parking.getName(),
            zone.getName(),
            space.getCode(),
            t.getEntryTime(),
            t.getExitTime(),
            t.getFormattedDuration(),
            t.getTotalAmount(),
            t.getStatus(),
            t.getPaymentStatus(),
            t.getCreatedAt()
      );
   }

   // ========================= HELPERS - PAGINACIÓN =========================

   private PagedResponse<ActiveTransactionDto> toActiveTransactionPagedResponse(PageResult<Transaction> result) {
      List<ActiveTransactionDto> content = result.content().stream()
            .map(this::buildActiveTransactionDto)
            .toList();
      return PagedResponse.of(content, result.pageNumber(), result.pageSize(), result.totalElements());
   }

   private PagedResponse<TransactionDto> toTransactionPagedResponse(PageResult<Transaction> result) {
      List<TransactionDto> content = result.content().stream()
            .map(this::buildTransactionDto)
            .toList();
      return PagedResponse.of(content, result.pageNumber(), result.pageSize(), result.totalElements());
   }

   // ========================= HELPERS - FORMATO =========================

   private String formatDuration(int minutes) {
      if (minutes <= 0) return "0min";
      int hours = minutes / 60;
      int mins = minutes % 60;

      if (hours > 0 && mins > 0) {
         return hours + "h " + mins + "min";
      }
      return hours > 0 ? hours + "h" : mins + "min";
   }
}
