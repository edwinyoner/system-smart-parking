package com.winnersystems.smartparking.parking.application.service.documenttype;

import com.winnersystems.smartparking.parking.application.dto.command.CreateDocumentTypeCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateDocumentTypeCommand;
import com.winnersystems.smartparking.parking.application.dto.query.DocumentTypeDto;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.port.input.documenttype.*;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.DocumentTypePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.DocumentType;
import com.winnersystems.smartparking.parking.domain.model.DocumentType.NumberRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso de los tipos de documento.
 *
 * Reglas:
 * - El código es único sin distinguir mayúsculas, también frente a los eliminados (409).
 * - Un tipo usado por clientes o transacciones no se elimina (409): esos registros perderían
 *   su identificación. Sí se puede desactivar: deja de ofrecerse en registros nuevos.
 * - Un tipo eliminado se restaura desde la papelera.
 * - La auditoría (createdBy, updatedBy, deletedBy) es el usuario del JWT.
 */
@Slf4j
@Service
@Transactional
public class DocumentTypeService implements
      CreateDocumentTypeUseCase,
      UpdateDocumentTypeUseCase,
      DeleteDocumentTypeUseCase,
      GetDocumentTypeUseCase,
      ListDocumentTypesUseCase,
      ToggleDocumentTypeStatusUseCase,
      RestoreDocumentTypeUseCase {

   private static final String RESOURCE = "Tipo de documento";

   private final DocumentTypePersistencePort persistencePort;
   private final CurrentUserPort currentUserPort;

   public DocumentTypeService(DocumentTypePersistencePort persistencePort, CurrentUserPort currentUserPort) {
      this.persistencePort = persistencePort;
      this.currentUserPort = currentUserPort;
   }

   // ========================= CREATE =========================

   @Override
   public DocumentTypeDto createDocumentType(CreateDocumentTypeCommand command) {
      Long userId = currentUserPort.requireCurrentUserId();

      // El dominio valida el código, el nombre, el código SUNAT y las reglas del número
      DocumentType type = DocumentType.create(
            command.code(),
            command.name(),
            command.description(),
            command.sunatCode(),
            new NumberRules(command.minLength(), command.maxLength(), command.numericOnly()),
            command.status() == null || command.status(),
            userId);

      ensureCodeIsAvailable(type.getCode());

      DocumentType saved = persistencePort.save(type);
      log.info("Tipo de documento creado: id={}, código={}, usuario={}", saved.getId(), saved.getCode(), userId);
      return toDto(saved);
   }

   // ========================= UPDATE =========================

   @Override
   public DocumentTypeDto updateDocumentType(Long documentTypeId, UpdateDocumentTypeCommand command) {
      DocumentType type = findOrThrow(documentTypeId);

      type.update(
            command.name(),
            command.description(),
            command.sunatCode(),
            new NumberRules(command.minLength(), command.maxLength(), command.numericOnly()));

      if (command.status() != null && command.status() != type.isActive()) {
         changeStatus(type, command.status());
      }

      type.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(persistencePort.save(type));
   }

   // ========================= DELETE =========================

   @Override
   public void deleteDocumentType(Long documentTypeId) {
      DocumentType type = findOrThrow(documentTypeId);

      long customers = persistencePort.countCustomersUsing(documentTypeId);
      long transactions = persistencePort.countTransactionsUsing(documentTypeId);
      if (customers > 0 || transactions > 0) {
         throw new ResourceConflictException(
               "No se puede eliminar el tipo " + type.getCode() + ": lo usan " + describeUsage(customers, transactions)
                     + ". Desactívalo para que no se ofrezca en registros nuevos.");
      }

      type.markAsDeleted(currentUserPort.requireCurrentUserId());
      persistencePort.save(type);
      log.info("Tipo de documento eliminado (soft delete): id={}", documentTypeId);
   }

   // ========================= RESTORE =========================

   @Override
   public DocumentTypeDto restoreDocumentType(Long documentTypeId) {
      DocumentType type = persistencePort.findDeletedById(documentTypeId)
            .orElseThrow(() -> new ResourceNotFoundException(
                  "Tipo de documento eliminado no encontrado con ID: " + documentTypeId));
      type.restore(currentUserPort.requireCurrentUserId());
      DocumentType saved = persistencePort.save(type);
      log.info("Tipo de documento restaurado: id={}, código={}", documentTypeId, saved.getCode());
      return toDto(saved);
   }

   // ========================= GET Y LIST =========================

   @Override
   @Transactional(readOnly = true)
   public DocumentTypeDto getDocumentTypeById(Long documentTypeId) {
      return toDto(findOrThrow(documentTypeId));
   }

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<DocumentTypeDto> listAllDocumentTypes(int page, int size, String search,
                                                              Boolean status, boolean deleted) {
      // La papelera muestra primero lo último que se eliminó
      PageRequest pageRequest = deleted
            ? PageRequest.of(page, size, "deletedAt", "DESC")
            : PageRequest.of(page, size, "id", "ASC");
      PageResult<DocumentType> result = persistencePort.findAll(pageRequest, search, deleted ? null : status, deleted);
      return result.toPagedResponse(this::toDto);
   }

   @Override
   @Transactional(readOnly = true)
   public List<DocumentTypeDto> listAllActiveDocumentTypes() {
      return persistencePort.findAllActive().stream()
            .map(this::toDto)
            .toList();
   }

   // ========================= ESTADO =========================

   @Override
   public DocumentTypeDto toggleDocumentTypeStatus(Long documentTypeId) {
      DocumentType type = findOrThrow(documentTypeId);
      return saveStatus(type, !type.isActive());
   }

   @Override
   public DocumentTypeDto activateDocumentTypeDto(Long documentTypeId) {
      return saveStatus(findOrThrow(documentTypeId), true);
   }

   @Override
   public DocumentTypeDto deactivateDocumentTypeDto(Long documentTypeId) {
      return saveStatus(findOrThrow(documentTypeId), false);
   }

   private DocumentTypeDto saveStatus(DocumentType type, boolean active) {
      changeStatus(type, active);
      type.updateModifiedBy(currentUserPort.requireCurrentUserId());
      return toDto(persistencePort.save(type));
   }

   /** Desactivar uno en uso está permitido: los clientes existentes conservan su documento */
   private void changeStatus(DocumentType type, boolean active) {
      if (active) {
         type.activate();
         return;
      }
      if (persistencePort.countCustomersUsing(type.getId()) > 0) {
         log.warn("Tipo de documento {} desactivado con clientes registrados", type.getCode());
      }
      type.deactivate();
   }

   // ========================= AUXILIARES =========================

   private DocumentType findOrThrow(Long id) {
      return persistencePort.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
   }

   private void ensureCodeIsAvailable(String code) {
      persistencePort.findByCodeIncludingDeleted(code).ifPresent(existing -> {
         throw new ResourceConflictException(existing.isDeleted()
               ? "El código " + code + " pertenece a un tipo de documento eliminado. Restáuralo desde Eliminados o usa otro código."
               : "Ya existe un tipo de documento con el código " + code);
      });
   }

   /** "3 clientes y 5 transacciones" */
   private static String describeUsage(long customers, long transactions) {
      String c = customers == 1 ? "1 cliente" : customers + " clientes";
      String t = transactions == 1 ? "1 transacción" : transactions + " transacciones";
      if (customers > 0 && transactions > 0) return c + " y " + t;
      return customers > 0 ? c : t;
   }

   private DocumentTypeDto toDto(DocumentType d) {
      boolean counted = d.getId() != null && !d.isDeleted();
      return new DocumentTypeDto(
            d.getId(),
            d.getCode(),
            d.getName(),
            d.getDescription(),
            d.getSunatCode(),
            d.getMinLength(),
            d.getMaxLength(),
            d.isNumericOnly(),
            d.describeLength(),
            d.getStatus(),
            counted ? persistencePort.countCustomersUsing(d.getId()) : 0,
            counted ? persistencePort.countTransactionsUsing(d.getId()) : 0,
            d.getCreatedAt(),
            d.getCreatedBy(),
            d.getUpdatedAt(),
            d.getUpdatedBy(),
            d.getDeletedAt(),
            d.getDeletedBy()
      );
   }
}
