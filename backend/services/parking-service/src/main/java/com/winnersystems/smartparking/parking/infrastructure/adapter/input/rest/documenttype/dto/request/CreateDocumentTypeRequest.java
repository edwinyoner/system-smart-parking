package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos de un tipo de documento nuevo.
 * Las reglas del número que no se envíen toman el valor por defecto (1 a 20, letras y dígitos).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDocumentTypeRequest {

   @NotBlank(message = "El código es obligatorio")
   @Pattern(regexp = "^[A-Za-z0-9_]{2,20}$", message = "El código tiene de 2 a 20 caracteres: letras, dígitos o guion bajo")
   private String code;
   @NotBlank(message = "El nombre es obligatorio")
   @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
   private String name;

   @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
   private String description;

   /** Catálogo N.° 06 de SUNAT: 1 = DNI, 4 = CE, 6 = RUC, 7 = pasaporte (opcional) */
   @Pattern(regexp = "^[A-Za-z0-9]{0,2}$", message = "El código SUNAT tiene 1 o 2 caracteres")
   private String sunatCode;

   @Min(value = 1, message = "La longitud mínima es 1")
   @Max(value = 20, message = "La longitud máxima es 20")
   private Integer minLength;

   @Min(value = 1, message = "La longitud mínima es 1")
   @Max(value = 20, message = "La longitud máxima es 20")
   private Integer maxLength;

   /** true = el número solo admite dígitos */
   private Boolean numericOnly;

   private Boolean status;
}
