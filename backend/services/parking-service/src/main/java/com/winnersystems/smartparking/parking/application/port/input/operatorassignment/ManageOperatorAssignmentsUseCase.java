package com.winnersystems.smartparking.parking.application.port.input.operatorassignment;

import com.winnersystems.smartparking.parking.application.dto.command.AssignOperatorCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateOperatorAssignmentCommand;
import com.winnersystems.smartparking.parking.application.dto.query.OperatorAssignmentDto;

import java.util.List;
import java.util.Optional;

/**
 * Asignación de operadores a parqueos y zonas por turno.
 */
public interface ManageOperatorAssignmentsUseCase {

   OperatorAssignmentDto assign(AssignOperatorCommand command);

   OperatorAssignmentDto update(Long assignmentId, UpdateOperatorAssignmentCommand command);

   /**
    * Termina la asignación desde hoy.
    *
    * @return la asignación finalizada, o vacío si aún no había empezado y se canceló
    */
   Optional<OperatorAssignmentDto> finish(Long assignmentId);

   OperatorAssignmentDto get(Long assignmentId);

   /**
    * Asignaciones filtradas: se exige el parqueo o el operador.
    *
    * @param zoneId null = todas (las de todo el parqueo y las de cada zona)
    * @param status null = todas; PENDING, ACTIVE o ENDED
    */
   List<OperatorAssignmentDto> list(Long parkingId, Long zoneId, Long operatorId, String status);

   /** Asignaciones de quien consulta (lo que el operador ve en su pantalla) */
   List<OperatorAssignmentDto> listMine(String status);
}
