package com.winnersystems.smartparking.parking.application.port.input.sensor;

import com.winnersystems.smartparking.parking.application.dto.command.SensorReadingCommand;
import com.winnersystems.smartparking.parking.application.dto.query.SensorReadingResult;

/**
 * Lecturas de los sensores HC-SR04 (las envía iot-service, que las recibe por MQTT).
 */
public interface RecordSensorReadingUseCase {

   /** 404 si ningún espacio vigente tiene ese sensor */
   SensorReadingResult recordReading(SensorReadingCommand command);
}
