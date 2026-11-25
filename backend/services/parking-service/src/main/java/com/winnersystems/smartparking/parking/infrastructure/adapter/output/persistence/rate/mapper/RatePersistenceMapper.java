package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.rate.mapper;

import com.winnersystems.smartparking.parking.domain.model.Rate;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.rate.entity.RateEntity;
import org.springframework.stereotype.Component;

/**
 * Conversión entre Rate (dominio) y RateEntity (JPA).
 */
@Component
public class RatePersistenceMapper {

   /** Reconstruye la tarifa guardada, sin volver a validarla */
   public Rate toDomain(RateEntity entity) {
      if (entity == null) {
         return null;
      }
      Rate rate = new Rate();
      rate.setId(entity.getId());
      rate.setName(entity.getName());
      rate.setDescription(entity.getDescription());
      rate.setAmount(entity.getAmount());
      rate.setCurrency(entity.getCurrency());
      rate.setBillingUnitMinutes(orDefault(entity.getBillingUnitMinutes(), Rate.DEFAULT_BILLING_UNIT_MINUTES));
      rate.setMinimumMinutes(orDefault(entity.getMinimumMinutes(), 0));
      rate.setGraceMinutes(orDefault(entity.getGraceMinutes(), Rate.DEFAULT_GRACE_MINUTES));
      rate.setToleranceMinutes(orDefault(entity.getToleranceMinutes(), Rate.DEFAULT_TOLERANCE_MINUTES));
      if (Boolean.TRUE.equals(entity.getStatus())) {
         rate.activate();
      } else {
         rate.deactivate();
      }
      // Auditoría (después de activate/deactivate, que tocan updatedAt)
      rate.setCreatedAt(entity.getCreatedAt());
      rate.setCreatedBy(entity.getCreatedBy());
      rate.setUpdatedAt(entity.getUpdatedAt());
      rate.setUpdatedBy(entity.getUpdatedBy());
      rate.setDeletedAt(entity.getDeletedAt());
      rate.setDeletedBy(entity.getDeletedBy());
      return rate;
   }

   public RateEntity toEntity(Rate rate) {
      if (rate == null) {
         return null;
      }
      RateEntity entity = new RateEntity();
      entity.setId(rate.getId());
      entity.setCreatedAt(rate.getCreatedAt());
      entity.setCreatedBy(rate.getCreatedBy());
      updateEntity(entity, rate);
      return entity;
   }

   /** Copia los campos modificables. No toca id ni createdAt/createdBy */
   public void updateEntity(RateEntity entity, Rate rate) {
      if (entity == null || rate == null) {
         return;
      }
      entity.setName(rate.getName());
      entity.setDescription(rate.getDescription());
      entity.setAmount(rate.getAmount());
      entity.setCurrency(rate.getCurrency());
      entity.setBillingUnitMinutes(rate.getBillingUnitMinutes());
      entity.setMinimumMinutes(rate.getMinimumMinutes());
      entity.setGraceMinutes(rate.getGraceMinutes());
      entity.setToleranceMinutes(rate.getToleranceMinutes());
      entity.setStatus(rate.getStatus());
      entity.setUpdatedAt(rate.getUpdatedAt());
      entity.setUpdatedBy(rate.getUpdatedBy());
      entity.setDeletedAt(rate.getDeletedAt());
      entity.setDeletedBy(rate.getDeletedBy());
   }

   private static int orDefault(Integer value, int defaultValue) {
      return value == null ? defaultValue : value;
   }
}
