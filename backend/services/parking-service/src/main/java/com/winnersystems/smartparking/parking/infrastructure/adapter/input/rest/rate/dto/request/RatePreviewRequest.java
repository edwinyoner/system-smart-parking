package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Simulación de cobro con los datos del formulario (aún sin guardar).
 *
 * @param durations minutos a simular (si viene vacío se usan ejemplos típicos)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatePreviewRequest {

   @NotNull(message = "El precio por hora es obligatorio")
   @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
   @Digits(integer = 8, fraction = 2, message = "El precio admite 8 dígitos enteros y 2 decimales")
   private BigDecimal amount;

   /** Fracción de cobro en minutos: 60 = por hora o fracción (por defecto) */
   @Min(value = 1, message = "La fracción de cobro es de al menos 1 minuto")
   @Max(value = 1440, message = "La fracción de cobro es de como máximo 1440 minutos")
   private Integer billingUnitMinutes;

   /** Tiempo mínimo que se cobra (0 = sin mínimo) */
   @Min(value = 0, message = "El tiempo mínimo no puede ser negativo")
   @Max(value = 1440, message = "El tiempo mínimo es de como máximo 1440 minutos")
   private Integer minimumMinutes;

   /** Si sale antes de este tiempo no paga (por defecto 5; 0 = siempre se cobra) */
   @Min(value = 0, message = "El tiempo de gracia no puede ser negativo")
   @Max(value = 120, message = "El tiempo de gracia es de como máximo 120 minutos")
   private Integer graceMinutes;

   /** Cortesía antes de cobrar la fracción siguiente (por defecto 5; menor que la fracción) */
   @Min(value = 0, message = "La tolerancia no puede ser negativa")
   private Integer toleranceMinutes;

   @Size(max = 20, message = "Como máximo 20 duraciones")
   private List<@Min(0) @Max(10080) Integer> durations;
}
