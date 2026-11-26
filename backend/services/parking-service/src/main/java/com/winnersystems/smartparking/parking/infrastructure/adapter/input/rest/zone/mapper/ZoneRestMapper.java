package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.mapper;

import com.winnersystems.smartparking.parking.application.dto.command.CreateZoneCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateZoneCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneDto;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.dto.request.CreateZoneRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.dto.request.UpdateZoneRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.dto.response.ZoneResponse;
import org.springframework.stereotype.Component;

/**
 * Convierte entre los DTO REST de zonas y los de la capa de aplicación.
 */
@Component
public class ZoneRestMapper {

   public CreateZoneCommand toCommand(CreateZoneRequest r) {
      return new CreateZoneCommand(r.parkingId(), r.name(), r.code(), r.address(), r.description(),
            r.latitude(), r.longitude(), r.cameraIds());
   }

   public UpdateZoneCommand toCommand(UpdateZoneRequest r) {
      return new UpdateZoneCommand(r.name(), r.address(), r.description(),
            r.latitude(), r.longitude(), r.cameraIds(), r.status());
   }

   public ZoneResponse toResponse(ZoneDto d) {
      return new ZoneResponse(
            d.id(), d.parkingId(), d.parkingName(), d.parkingCode(),
            d.name(), d.code(), d.address(), d.description(),
            d.totalSpaces(), d.availableSpaces(), d.occupiedSpaces(), d.unavailableSpaces(),
            d.occupancyPercentage(), d.latitude(), d.longitude(),
            d.hasCamera(), d.cameraIds(), d.cameraCount(),
            d.status(), d.operational(),
            d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy(),
            d.deletedAt(), d.deletedBy()
      );
   }
}
