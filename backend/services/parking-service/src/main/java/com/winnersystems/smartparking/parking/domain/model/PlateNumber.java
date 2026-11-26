package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;

import java.util.regex.Pattern;

/**
 * Regla única para la placa peruana. La usan la entrada manual, la entrada por cámara
 * y la cola de revisión, para que "abc 123", "ABC123" y "ABC-123" sean la misma placa.
 *
 * Formatos aceptados (6 caracteres sin el guion):
 * - Antiguo: 3 letras + 3 números      (ABC-123)
 * - Nuevo:   letra, número, letra + 3 números (A1B-234)
 *
 * Siempre se guarda con guion: ABC-123.
 */
public final class PlateNumber {

   private static final Pattern OLD_FORMAT = Pattern.compile("^[A-Z]{3}[0-9]{3}$");
   private static final Pattern NEW_FORMAT = Pattern.compile("^[A-Z][0-9][A-Z][0-9]{3}$");

   private PlateNumber() {
   }

   /**
    * Normaliza y valida una placa.
    *
    * @param raw placa tal como llega (del operador o de la IA)
    * @return la placa en formato ABC-123
    * @throws ParkingDomainException si está vacía o no tiene un formato peruano válido
    */
   public static String normalize(String raw) {
      if (raw == null || raw.isBlank()) {
         throw new ParkingDomainException("La placa es obligatoria");
      }
      // Se quitan espacios, guiones y puntos que suelen aparecer al escribir o en el OCR
      String cleaned = raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
      if (!OLD_FORMAT.matcher(cleaned).matches() && !NEW_FORMAT.matcher(cleaned).matches()) {
         throw new ParkingDomainException(
               "La placa '" + raw.trim() + "' no tiene un formato peruano válido (ej.: ABC-123 o A1B-234)");
      }
      return cleaned.substring(0, 3) + "-" + cleaned.substring(3);
   }

   /** true si la placa tiene un formato válido (no lanza excepción) */
   public static boolean isValid(String raw) {
      try {
         normalize(raw);
         return true;
      } catch (ParkingDomainException e) {
         return false;
      }
   }
}
