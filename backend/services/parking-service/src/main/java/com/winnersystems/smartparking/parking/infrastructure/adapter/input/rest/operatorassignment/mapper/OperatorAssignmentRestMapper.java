package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.mapper;

import com.winnersystems.smartparking.parking.application.dto.command.AssignOperatorCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateOperatorAssignmentCommand;
import com.winnersystems.smartparking.parking.application.dto.query.OperatorAssignmentDto;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.dto.request.AssignOperatorRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.dto.request.UpdateOperatorAssignmentRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.dto.response.OperatorAssignmentResponse;
import org.springframework.stereotype.Component;

@Component
public class OperatorAssignmentRestMapper {

   public AssignOperatorCommand toCommand(AssignOperatorRequest r) {
      return new AssignOperatorCommand(r.operatorId(), r.parkingId(), r.zoneId(), r.shiftId(), r.startDate(), r.endDate());
   }

   public UpdateOperatorAssignmentCommand toCommand(UpdateOperatorAssignmentRequest r) {
      return new UpdateOperatorAssignmentCommand(r.startDate(), r.endDate());
   }

   public OperatorAssignmentResponse toResponse(OperatorAssignmentDto d) {
      return new OperatorAssignmentResponse(
            d.id(), d.operatorId(), d.operatorName(),
            d.parkingId(), d.parkingName(), d.parkingCode(), d.scope(),
            d.zoneId(), d.zoneName(), d.zoneCode(),
            d.shiftId(), d.shiftName(), d.shiftStartTime(), d.shiftEndTime(), d.shiftCrossesMidnight(),
            d.startDate(), d.endDate(), d.status(), d.onDutyNow(),
            d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy()
      );
   }
}
