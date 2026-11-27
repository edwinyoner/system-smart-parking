package com.winnersystems.smartparking.parking.application.port.input.customer;

import com.winnersystems.smartparking.parking.application.dto.command.UpdateCustomerContactCommand;
import com.winnersystems.smartparking.parking.application.dto.query.*;

/**
 * Consulta de clientes e historial. No se crean ni eliminan a mano: nacen en la entrada
 * cuando el conductor da su documento. Solo se editan sus datos de contacto.
 */
public interface ManageCustomersUseCase {

   /** @param search parte del nombre, del documento o del celular */
   PageResult<CustomerDto> list(String search, PageRequest pageRequest);

   CustomerDetailDto get(Long customerId);

   PageResult<StayHistoryDto> stays(Long customerId, PageRequest pageRequest);

   CustomerDto updateContact(Long customerId, UpdateCustomerContactCommand command);
}
