package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.mapper;

import com.winnersystems.smartparking.parking.domain.model.DocumentType;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.entity.DocumentTypeEntity;
import org.springframework.stereotype.Component;

@Component
public class DocumentTypePersistenceMapper {

   public DocumentType toDomain(DocumentTypeEntity entity) {
      if (entity == null) return null;

      DocumentType type = new DocumentType(entity.getCode(), entity.getName());
      type.setId(entity.getId());
      type.setDescription(entity.getDescription());
      type.setSunatCode(entity.getSunatCode());
      type.setMinLength(entity.getMinLength() != null ? entity.getMinLength() : 1);
      type.setMaxLength(entity.getMaxLength() != null ? entity.getMaxLength() : DocumentType.MAX_NUMBER_LENGTH);
      type.setNumericOnly(Boolean.TRUE.equals(entity.getNumericOnly()));
      if (Boolean.TRUE.equals(entity.getStatus())) {
         type.activate();
      } else {
         type.deactivate();
      }
      type.setCreatedAt(entity.getCreatedAt());
      type.setCreatedBy(entity.getCreatedBy());
      type.setUpdatedAt(entity.getUpdatedAt());
      type.setUpdatedBy(entity.getUpdatedBy());
      type.setDeletedAt(entity.getDeletedAt());
      type.setDeletedBy(entity.getDeletedBy());
      return type;
   }

   public DocumentTypeEntity toEntity(DocumentType domain) {
      if (domain == null) return null;
      return DocumentTypeEntity.builder()
            .id(domain.getId())
            .code(domain.getCode())
            .name(domain.getName())
            .description(domain.getDescription())
            .sunatCode(domain.getSunatCode())
            .minLength(domain.getMinLength())
            .maxLength(domain.getMaxLength())
            .numericOnly(domain.isNumericOnly())
            .status(domain.getStatus())
            .createdAt(domain.getCreatedAt())
            .createdBy(domain.getCreatedBy())
            .updatedAt(domain.getUpdatedAt())
            .updatedBy(domain.getUpdatedBy())
            .deletedAt(domain.getDeletedAt())
            .deletedBy(domain.getDeletedBy())
            .build();
   }

   /** Copia los cambios sobre la entidad existente (el código y la creación no cambian) */
   public void updateEntity(DocumentTypeEntity entity, DocumentType domain) {
      if (entity == null || domain == null) return;
      entity.setName(domain.getName());
      entity.setDescription(domain.getDescription());
      entity.setSunatCode(domain.getSunatCode());
      entity.setMinLength(domain.getMinLength());
      entity.setMaxLength(domain.getMaxLength());
      entity.setNumericOnly(domain.isNumericOnly());
      entity.setStatus(domain.getStatus());
      entity.setUpdatedAt(domain.getUpdatedAt());
      entity.setUpdatedBy(domain.getUpdatedBy());
      entity.setDeletedAt(domain.getDeletedAt());
      entity.setDeletedBy(domain.getDeletedBy());
   }
}
