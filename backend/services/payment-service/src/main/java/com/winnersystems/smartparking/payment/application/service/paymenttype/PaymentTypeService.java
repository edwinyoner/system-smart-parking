package com.winnersystems.smartparking.payment.application.service.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.command.CreatePaymentTypeCommand;
import com.winnersystems.smartparking.payment.application.dto.command.UpdatePaymentTypeCommand;
import com.winnersystems.smartparking.payment.application.dto.query.PageRequest;
import com.winnersystems.smartparking.payment.application.dto.query.PageResult;
import com.winnersystems.smartparking.payment.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentTypeDto;
import com.winnersystems.smartparking.payment.application.port.input.paymenttype.*;
import com.winnersystems.smartparking.payment.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.payment.application.port.output.PaymentPersistencePort;
import com.winnersystems.smartparking.payment.application.port.output.PaymentTypePersistencePort;
import com.winnersystems.smartparking.payment.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.payment.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.domain.model.PaymentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso de los tipos de pago.
 *
 * Reglas:
 * - El código es único sin distinguir mayúsculas, también frente a los eliminados (409).
 * - Un tipo eliminado se restaura desde la papelera.
 * - La auditoría (createdBy, updatedBy, deletedBy) es el usuario del JWT.
 * - Un tipo con pagos no se elimina (409): los pagos lo referencian; sí se desactiva.
 */
@Slf4j
@Service
@Transactional
public class PaymentTypeService implements
      CreatePaymentTypeUseCase,
      UpdatePaymentTypeUseCase,
      DeletePaymentTypeUseCase,
      GetPaymentTypeUseCase,
      ListPaymentTypesUseCase,
      TogglePaymentTypeStatusUseCase,
      RestorePaymentTypeUseCase {

   private static final String RESOURCE = "Tipo de pago";

   private final PaymentTypePersistencePort persistencePort;
   private final CurrentUserPort currentUserPort;
   private final PaymentPersistencePort paymentPersistencePort;

   public PaymentTypeService(PaymentTypePersistencePort persistencePort, CurrentUserPort currentUserPort,
                             PaymentPersistencePort paymentPersistencePort) {
      this.persistencePort = persistencePort;
      this.currentUserPort = currentUserPort;
      this.paymentPersistencePort = paymentPersistencePort;
   }

   // ========================= CREATE =========================

   @Override
   public PaymentTypeDto createPaymentType(CreatePaymentTypeCommand command) {
      Long userId = currentUserPort.requireCurrentUserId();

      // El dominio valida el código, el modo de confirmación y los canales
      PaymentType type = PaymentType.create(
            command.code(),
            command.name(),
            command.description(),
            command.confirmationMode(),
            command.requiresReference(),
            command.channels(),
            command.status() == null || command.status(),
            userId);

      ensureCodeIsAvailable(type.getCode());

      PaymentType saved = persistencePort.save(type);
      log.info("Tipo de pago creado: id={}, código={}, usuario={}", saved.getId(), saved.getCode(), userId);
      return toDto(saved);
   }

   // ========================= UPDATE =========================

   @Override
   public PaymentTypeDto updatePaymentType(Long paymentTypeId, UpdatePaymentTypeCommand command) {
      PaymentType type = findOrThrow(paymentTypeId);

      type.update(
            command.name(),
            command.description(),
            command.confirmationMode(),
            command.requiresReference(),
            command.channels());

      if (command.status() != null && command.status() != type.isActive()) {
         if (command.status()) {
            type.activate();
         } else {
            type.deactivate();
         }
      }

      type.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(persistencePort.save(type));
   }

   // ========================= DELETE Y RESTORE =========================

   @Override
   public void deletePaymentType(Long paymentTypeId) {
      PaymentType type = findOrThrow(paymentTypeId);
      long payments = paymentPersistencePort.countByPaymentTypeId(paymentTypeId);
      if (payments > 0) {
         throw new ResourceConflictException("No se puede eliminar " + type.getName() + ": tiene " + payments
               + " pago(s) registrado(s). Puedes desactivarlo.");
      }
      type.markAsDeleted(currentUserPort.requireCurrentUserId());
      persistencePort.save(type);
      log.info("Tipo de pago eliminado (soft delete): id={}", paymentTypeId);
   }

   @Override
   public PaymentTypeDto restorePaymentType(Long paymentTypeId) {
      PaymentType type = persistencePort.findDeletedById(paymentTypeId)
            .orElseThrow(() -> new ResourceNotFoundException(
                  "Tipo de pago eliminado no encontrado con ID: " + paymentTypeId));
      type.restore(currentUserPort.requireCurrentUserId());
      PaymentType saved = persistencePort.save(type);
      log.info("Tipo de pago restaurado: id={}, código={}", paymentTypeId, saved.getCode());
      return toDto(saved);
   }

   // ========================= GET Y LIST =========================

   @Override
   @Transactional(readOnly = true)
   public PaymentTypeDto getPaymentTypeById(Long paymentTypeId) {
      return toDto(findOrThrow(paymentTypeId));
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<PaymentTypeDto> listAllPaymentTypes(int page, int size, String search,
                                                            Boolean status, boolean deleted) {
      // La papelera muestra primero lo último que se eliminó
      PageRequest pageRequest = deleted
            ? PageRequest.of(page, size, "deletedAt", "DESC")
            : PageRequest.of(page, size, "id", "ASC");
      PageResult<PaymentType> result = persistencePort.findAll(pageRequest, search, deleted ? null : status, deleted);
      return result.toPagedResponse(this::toDto);
   }

   @Override
   @Transactional(readOnly = true)
   public List<PaymentTypeDto> listActivePaymentTypes(PaymentChannel channel) {
      return persistencePort.findAllActive(channel).stream()
            .map(this::toDto)
            .toList();
   }

   // ========================= ESTADO =========================

   @Override
   public PaymentTypeDto togglePaymentTypeStatus(Long paymentTypeId) {
      PaymentType type = findOrThrow(paymentTypeId);
      return saveStatus(type, !type.isActive());
   }

   @Override
   public PaymentTypeDto activatePaymentType(Long paymentTypeId) {
      return saveStatus(findOrThrow(paymentTypeId), true);
   }

   @Override
   public PaymentTypeDto deactivatePaymentType(Long paymentTypeId) {
      return saveStatus(findOrThrow(paymentTypeId), false);
   }

   private PaymentTypeDto saveStatus(PaymentType type, boolean active) {
      if (active) {
         type.activate();
      } else {
         type.deactivate();
      }
      type.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(persistencePort.save(type));
   }

   // ========================= AUXILIARES =========================

   private PaymentType findOrThrow(Long id) {
      return persistencePort.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
   }

   private void ensureCodeIsAvailable(String code) {
      persistencePort.findByCodeIncludingDeleted(code).ifPresent(existing -> {
         throw new ResourceConflictException(existing.isDeleted()
               ? "El código " + code + " pertenece a un tipo de pago eliminado. Restáuralo desde Eliminados o usa otro código."
               : "Ya existe un tipo de pago con el código " + code);
      });
   }

   private PaymentTypeDto toDto(PaymentType t) {
      return new PaymentTypeDto(
            t.getId(),
            t.getCode(),
            t.getName(),
            t.getDescription(),
            t.getConfirmationMode(),
            t.isRequiresReference(),
            t.getChannels(),
            t.getStatus(),
            t.getId() == null || t.isDeleted() ? 0 : paymentPersistencePort.countByPaymentTypeId(t.getId()),
            t.getCreatedAt(),
            t.getCreatedBy(),
            t.getUpdatedAt(),
            t.getUpdatedBy(),
            t.getDeletedAt(),
            t.getDeletedBy()
      );
   }
}
