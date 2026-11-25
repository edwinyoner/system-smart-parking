package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift.mapper;

import com.winnersystems.smartparking.parking.application.dto.command.CreateShiftCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateShiftCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ShiftDto;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift.dto.request.CreateShiftRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift.dto.request.UpdateShiftRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift.dto.response.ShiftResponse;
import org.springframework.stereotype.Component;

/**
 * Convierte entre los DTO REST y los de la capa de aplicación.
 */
@Component
public class ShiftRestMapper {

   public CreateShiftCommand toCommand(CreateShiftRequest request) {
      return new CreateShiftCommand(
            request.getName(),
            request.getCode(),
            request.getDescription(),
            request.getStartTime(),
            request.getEndTime(),
            request.getStatus());   // Antes se descartaba y el turno nacía siempre inactivo
   }

   public UpdateShiftCommand toCommand(UpdateShiftRequest request) {
      return new UpdateShiftCommand(
            request.getName(),
            request.getDescription(),
            request.getStartTime(),
            request.getEndTime(),
            request.getStatus());
   }

   public ShiftResponse toResponse(ShiftDto dto) {
      if (dto == null) return null;
      return new ShiftResponse(
            dto.id(),
            dto.name(),
            dto.code(),
            dto.description(),
            dto.startTime(),
            dto.endTime(),
            dto.status(),
            dto.crossesMidnight(),
            dto.durationHours(),
            dto.usedByParkings(),
            dto.createdAt(),
            dto.createdBy(),
            dto.updatedAt(),
            dto.updatedBy(),
            dto.deletedAt(),
            dto.deletedBy());
   }
}
