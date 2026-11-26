package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.mapper;

import com.winnersystems.smartparking.parking.application.dto.command.CreateParkingCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateParkingCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingDto;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.dto.request.CreateParkingRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.dto.request.UpdateParkingRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.dto.response.ParkingResponse;
import org.springframework.stereotype.Component;

/**
 * Convierte entre los DTO REST de parqueos y los de la capa de aplicación.
 */
@Component
public class ParkingRestMapper {

   public CreateParkingCommand toCommand(CreateParkingRequest r) {
      return new CreateParkingCommand(r.name(), r.code(), r.description(), r.address(),
            r.latitude(), r.longitude(), r.managerId());
   }

   public UpdateParkingCommand toCommand(UpdateParkingRequest r) {
      return new UpdateParkingCommand(r.name(), r.description(), r.address(),
            r.latitude(), r.longitude(), r.managerId(), r.status());
   }

   public ParkingResponse toResponse(ParkingDto d) {
      return new ParkingResponse(
            d.id(), d.name(), d.code(), d.description(), d.address(),
            d.latitude(), d.longitude(), d.managerId(), d.managerName(),
            d.totalZones(), d.totalSpaces(), d.availableSpaces(), d.occupiedSpaces(),
            d.unavailableSpaces(), d.occupancyPercentage(),
            d.status(), d.operational(),
            d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy(),
            d.deletedAt(), d.deletedBy()
      );
   }
}
