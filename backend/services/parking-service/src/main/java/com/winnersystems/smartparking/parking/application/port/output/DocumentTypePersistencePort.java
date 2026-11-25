package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.domain.model.DocumentType;

import java.util.List;
import java.util.Optional;

public interface DocumentTypePersistencePort {

   DocumentType save(DocumentType documentType);

   /** Solo vigentes (no eliminados) */
   Optional<DocumentType> findById(Long id);

   /** Vigente o eliminado: para mostrar el nombre en clientes y transacciones antiguas */
   Optional<DocumentType> findByIdIncludingDeleted(Long id);

   /** Solo de la papelera */
   Optional<DocumentType> findDeletedById(Long id);

   /** Código sin distinguir mayúsculas, también entre los eliminados */
   Optional<DocumentType> findByCodeIncludingDeleted(String code);

   List<DocumentType> findAllActive();

   /** @param deleted true = papelera; false = vigentes */
   PageResult<DocumentType> findAll(PageRequest pageRequest, String search, Boolean status, boolean deleted);

   /** Clientes con este tipo de documento (también los eliminados) */
   long countCustomersUsing(Long documentTypeId);

   /** Transacciones que lo usaron en la entrada o en la salida */
   long countTransactionsUsing(Long documentTypeId);
}
