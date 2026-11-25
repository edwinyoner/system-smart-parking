package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.port.output.DocumentTypePersistencePort;
import com.winnersystems.smartparking.parking.domain.model.DocumentType;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.entity.DocumentTypeEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.mapper.DocumentTypePersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.repository.DocumentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DocumentTypePersistenceAdapter implements DocumentTypePersistencePort {

   private final DocumentTypeRepository repository;
   private final DocumentTypePersistenceMapper mapper;

   // ========================= CREATE/UPDATE =========================

   @Override
   public DocumentType save(DocumentType documentType) {
      DocumentTypeEntity entity;

      if (documentType.getId() != null) {
         entity = repository.findById(documentType.getId())
               .orElseThrow(() -> new IllegalArgumentException(
                     "Tipo de documento no encontrado con ID: " + documentType.getId()));
         mapper.updateEntity(entity, documentType);
      } else {
         entity = mapper.toEntity(documentType);
      }

      return mapper.toDomain(repository.save(entity));
   }

   // ========================= READ =========================

   @Override
   public Optional<DocumentType> findById(Long id) {
      return repository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<DocumentType> findByIdIncludingDeleted(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public Optional<DocumentType> findDeletedById(Long id) {
      return repository.findByIdAndDeletedAtIsNotNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<DocumentType> findByCodeIncludingDeleted(String code) {
      return repository.findFirstByCodeIgnoreCase(code.trim()).map(mapper::toDomain);
   }

   @Override
   public List<DocumentType> findAllActive() {
      return repository.findByStatusTrueAndDeletedAtIsNullOrderByIdAsc().stream()
            .map(mapper::toDomain)
            .toList();
   }

   @Override
   public PageResult<DocumentType> findAll(PageRequest pageRequest, String search, Boolean status, boolean deleted) {
      String text = search == null || search.isBlank() ? null : search.trim().toLowerCase();
      // %, _ y \ escritos por el usuario se buscan como texto, no como comodines
      if (text != null) {
         text = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      }
      Page<DocumentTypeEntity> page = repository.search(text, status, deleted, buildPageable(pageRequest));
      return PageResult.of(
            page.getContent().stream().map(mapper::toDomain).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements()
      );
   }

   @Override
   public long countCustomersUsing(Long documentTypeId) {
      return repository.countCustomersUsing(documentTypeId);
   }

   @Override
   public long countTransactionsUsing(Long documentTypeId) {
      return repository.countTransactionsUsing(documentTypeId);
   }

   private Pageable buildPageable(PageRequest req) {
      Sort sort = req.hasSorting()
            ? Sort.by(Sort.Direction.fromString(req.sortDirection()), req.sortBy())
            : Sort.by(Sort.Direction.ASC, "id");
      return org.springframework.data.domain.PageRequest.of(req.page(), req.size(), sort);
   }
}
