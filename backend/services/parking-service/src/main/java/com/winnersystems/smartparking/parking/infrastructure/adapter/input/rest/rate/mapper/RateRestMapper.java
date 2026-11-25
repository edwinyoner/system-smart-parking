package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.mapper;

import com.winnersystems.smartparking.parking.application.dto.command.CreateRateCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateRateCommand;
import com.winnersystems.smartparking.parking.application.dto.query.RateDto;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.dto.request.CreateRateRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.dto.request.UpdateRateRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.dto.response.RateResponse;
import org.springframework.stereotype.Component;

/**
 * Convierte entre los DTO REST y los de la capa de aplicación.
 */
@Component
public class RateRestMapper {

   public CreateRateCommand toCommand(CreateRateRequest r) {
      return new CreateRateCommand(r.getName(), r.getDescription(), r.getAmount(),
            r.getBillingUnitMinutes(), r.getMinimumMinutes(), r.getGraceMinutes(), r.getToleranceMinutes(),
            r.getStatus());   // Antes se descartaba y la tarifa nacía siempre inactiva
   }

   public UpdateRateCommand toCommand(UpdateRateRequest r) {
      return new UpdateRateCommand(r.getName(), r.getDescription(), r.getAmount(),
            r.getBillingUnitMinutes(), r.getMinimumMinutes(), r.getGraceMinutes(), r.getToleranceMinutes(),
            r.getStatus());
   }

   public RateResponse toResponse(RateDto d) {
      if (d == null) return null;
      return new RateResponse(d.id(), d.name(), d.description(), d.amount(), d.currency(),
            d.billingUnitMinutes(), d.minimumMinutes(), d.graceMinutes(), d.toleranceMinutes(),
            d.fractionAmount(), d.status(), d.usedByParkings(),
            d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy(), d.deletedAt(), d.deletedBy());
   }
}
