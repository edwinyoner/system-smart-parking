package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.mapper;

import com.winnersystems.smartparking.parking.application.dto.command.CreateSpaceCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateSpaceCommand;
import com.winnersystems.smartparking.parking.application.dto.query.SpaceDto;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.dto.request.CreateSpaceRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.dto.request.UpdateSpaceRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.dto.response.SpaceResponse;
import org.springframework.stereotype.Component;

/**
 * Convierte entre los DTO REST de espacios y los de la capa de aplicación.
 */
@Component
public class SpaceRestMapper {

   public CreateSpaceCommand toCommand(CreateSpaceRequest r) {
      return new CreateSpaceCommand(r.zoneId(), r.code(), r.type(), r.description(), r.width(), r.length(),
            r.sensorId(), Boolean.TRUE.equals(r.hasCameraCoverage()));
   }

   public UpdateSpaceCommand toCommand(UpdateSpaceRequest r) {
      return new UpdateSpaceCommand(r.type(), r.description(), r.width(), r.length(), r.sensorId(),
            Boolean.TRUE.equals(r.hasCameraCoverage()), r.status());
   }

   public SpaceResponse toResponse(SpaceDto d) {
      return new SpaceResponse(
            d.id(), d.zoneId(), d.zoneName(), d.zoneCode(), d.parkingId(), d.parkingName(),
            d.type(), d.typeName(), d.code(), d.description(), d.width(), d.length(), d.area(),
            d.hasSensor(), d.sensorId(), d.hasCameraCoverage(), d.status(), d.receivesVehicles(),
            d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy(), d.deletedAt(), d.deletedBy()
      );
   }
}
