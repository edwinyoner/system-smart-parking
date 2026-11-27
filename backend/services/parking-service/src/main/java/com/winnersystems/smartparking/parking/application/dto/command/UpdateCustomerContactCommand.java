package com.winnersystems.smartparking.parking.application.dto.command;

/** Datos de contacto de un cliente; vacío borra el dato */
public record UpdateCustomerContactCommand(String phone, String email, String address) {
}
