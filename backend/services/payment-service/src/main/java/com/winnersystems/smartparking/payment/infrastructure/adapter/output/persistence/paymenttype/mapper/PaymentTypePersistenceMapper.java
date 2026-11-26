package com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype.mapper;

import com.winnersystems.smartparking.payment.domain.model.PaymentType;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype.entity.PaymentTypeEntity;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class PaymentTypePersistenceMapper {

   public PaymentType toDomain(PaymentTypeEntity entity) {
      if (entity == null) return null;
      PaymentType type = new PaymentType();
      type.setId(entity.getId());
      type.setCode(entity.getCode());
      type.setName(entity.getName());
      type.setDescription(entity.getDescription());
      type.setConfirmationMode(entity.getConfirmationMode());
      type.setRequiresReference(Boolean.TRUE.equals(entity.getRequiresReference()));
      type.setChannels(entity.getChannels());
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

   /** Copia el dominio sobre una entidad nueva o existente (el código y la creación solo al crear) */
   public void copyToEntity(PaymentType domain, PaymentTypeEntity entity) {
      if (entity.getId() == null) {
         entity.setCode(domain.getCode());
         entity.setCreatedAt(domain.getCreatedAt());
         entity.setCreatedBy(domain.getCreatedBy());
      }
      entity.setName(domain.getName());
      entity.setDescription(domain.getDescription());
      entity.setConfirmationMode(domain.getConfirmationMode());
      entity.setRequiresReference(domain.isRequiresReference());
      // Se reemplaza el contenido, no la colección: Hibernate sigue los cambios de la misma instancia
      if (entity.getChannels() == null) {
         entity.setChannels(new HashSet<>());
      }
      entity.getChannels().clear();
      entity.getChannels().addAll(domain.getChannels());
      entity.setStatus(domain.getStatus());
      entity.setUpdatedAt(domain.getUpdatedAt());
      entity.setUpdatedBy(domain.getUpdatedBy());
      entity.setDeletedAt(domain.getDeletedAt());
      entity.setDeletedBy(domain.getDeletedBy());
   }
}
