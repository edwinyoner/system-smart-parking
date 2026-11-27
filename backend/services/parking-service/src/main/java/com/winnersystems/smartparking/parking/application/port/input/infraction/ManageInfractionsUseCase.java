package com.winnersystems.smartparking.parking.application.port.input.infraction;

import com.winnersystems.smartparking.parking.application.dto.command.ChangeInfractionFineCommand;
import com.winnersystems.smartparking.parking.application.dto.command.EditInfractionCommand;
import com.winnersystems.smartparking.parking.application.dto.command.RegisterInfractionCommand;
import com.winnersystems.smartparking.parking.application.dto.query.InfractionDto;
import com.winnersystems.smartparking.parking.application.dto.query.InfractionFilter;
import com.winnersystems.smartparking.parking.application.dto.query.InfractionSummaryDto;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;

import java.util.List;

/**
 * Registro, consulta y resolución de infracciones.
 * El cobro de la multa lo hace payment-service (concepto FINE) y avisa por /internal/charges.
 */
public interface ManageInfractionsUseCase {

   InfractionDto register(RegisterInfractionCommand command);

   InfractionDto get(Long infractionId);

   PageResult<InfractionDto> list(InfractionFilter filter, PageRequest pageRequest);

   /** Infracciones de una estadía (para su detalle) */
   List<InfractionDto> listByTransaction(Long transactionId);

   InfractionSummaryDto summary(Long parkingId);

   InfractionDto edit(Long infractionId, EditInfractionCommand command);

   InfractionDto changeFine(Long infractionId, ChangeInfractionFineCommand command);

   /** Anula con un motivo (sin pagos) */
   InfractionDto dismiss(Long infractionId, String reason);

   /** Cierra con amonestación (sin multa) */
   InfractionDto closeWithWarning(Long infractionId, String comment);
}
