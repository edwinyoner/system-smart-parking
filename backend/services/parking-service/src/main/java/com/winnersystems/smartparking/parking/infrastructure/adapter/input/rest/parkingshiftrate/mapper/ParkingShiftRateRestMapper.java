package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.mapper;

import com.winnersystems.smartparking.parking.application.dto.command.ConfigureParkingShiftRatesCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingScheduleStatusDto;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingShiftRateDto;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.request.ConfigureParkingShiftRatesRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.response.ParkingScheduleStatusResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.response.ParkingShiftRateResponse;
import org.springframework.stereotype.Component;

/**
 * Convierte entre los DTO REST de horarios y tarifas del parqueo y los de aplicación.
 */
@Component
public class ParkingShiftRateRestMapper {

   public ConfigureParkingShiftRatesCommand toCommand(Long parkingId, ConfigureParkingShiftRatesRequest r) {
      return new ConfigureParkingShiftRatesCommand(parkingId, r.configurations().stream()
            .map(c -> new ConfigureParkingShiftRatesCommand.Item(c.shiftId(), c.rateId(), c.status()))
            .toList());
   }

   public ParkingShiftRateResponse toResponse(ParkingShiftRateDto d) {
      if (d == null) return null;
      return new ParkingShiftRateResponse(
            d.id(), d.parkingId(), d.shiftId(), d.shiftName(), d.shiftCode(),
            d.startTime(), d.endTime(), d.crossesMidnight(), d.shiftUsable(),
            d.rateId(), d.rateName(), d.rateAmount(), d.currency(), d.rateUsable(),
            d.status(), d.usable(),
            d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy()
      );
   }

   public ParkingScheduleStatusResponse toResponse(ParkingScheduleStatusDto d) {
      return new ParkingScheduleStatusResponse(d.parkingId(), d.at(), d.open(), toResponse(d.current()));
   }
}
