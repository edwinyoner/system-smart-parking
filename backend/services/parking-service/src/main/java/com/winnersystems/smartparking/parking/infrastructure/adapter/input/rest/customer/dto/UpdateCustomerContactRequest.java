package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.customer.dto;

import jakarta.validation.constraints.Size;

/** El formato del celular y del correo lo valida el dominio */
public record UpdateCustomerContactRequest(
      @Size(max = 15) String phone,
      @Size(max = 100) String email,
      @Size(max = 200) String address) {
}
