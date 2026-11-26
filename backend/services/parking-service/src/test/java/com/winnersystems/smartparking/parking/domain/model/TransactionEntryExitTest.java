package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.DocumentMismatchException;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.PaymentRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Entrada manual o por cámara con las mismas reglas, documento opcional y el interruptor
 * anti-robo del parqueo en la salida.
 */
class TransactionEntryExitTest {

   private static final LocalDateTime ENTRY = LocalDateTime.of(2026, 11, 26, 8, 0);
   private static final int WINDOW = 15;
   private static final Long DNI = 1L;

   /** 5 min de gracia: salir a los 3 min no cuesta nada, así se prueba la salida sin pagos */
   private final Rate rate = Rate.create("Estándar", null, new BigDecimal("2.50"),
         new Rate.BillingRules(60, 0, 5, 5), true, 1L);

   private static Transaction.EntryData manual(Long customerId, Long docType, String docNumber, Long operatorId) {
      return new Transaction.EntryData(10L, customerId, 1L, 2L, 3L, 4L, docType, docNumber,
            Transaction.METHOD_MANUAL, operatorId, null, null, null);
   }

   private static Transaction.EntryData camera(String captureId, Double confidence) {
      return new Transaction.EntryData(10L, null, 1L, 2L, 3L, 4L, null, null,
            Transaction.METHOD_CAMERA_AI, null, captureId, "https://storage/entrada.jpg", confidence);
   }

   private static Transaction active(Transaction.EntryData data) {
      Transaction t = Transaction.registerEntry(data, ENTRY);
      t.setId(99L);
      return t;
   }

   // ========================= ENTRADA =========================

   @Test
   @DisplayName("entrada manual solo con placa: sin documento ni cliente, con operador")
   void manualEntryWithoutDocument() {
      Transaction t = Transaction.registerEntry(manual(null, null, null, 7L), ENTRY);
      assertThat(t.isActive()).isTrue();
      assertThat(t.isPending()).isTrue();
      assertThat(t.hasDriverDocument()).isFalse();
      assertThat(t.getCustomerId()).isNull();
      assertThat(t.getEntryOperatorId()).isEqualTo(7L);
      assertThat(t.getEntryMethod()).isEqualTo(Transaction.METHOD_MANUAL);
      assertThat(t.getEntryTime()).isEqualTo(ENTRY);
      assertThat(t.hasCompleteEntryData()).isTrue();
   }

   @Test
   @DisplayName("entrada manual con documento: exige el cliente y guarda el documento")
   void manualEntryWithDocument() {
      Transaction t = Transaction.registerEntry(manual(5L, DNI, "12345678", 7L), ENTRY);
      assertThat(t.hasDriverDocument()).isTrue();
      assertThat(t.getCustomerId()).isEqualTo(5L);
      assertThat(t.getEntryDocumentNumber()).isEqualTo("12345678");
   }

   @Test
   @DisplayName("entrada por cámara: sin operador, con captura, foto y confianza")
   void cameraEntry() {
      Transaction t = Transaction.registerEntry(camera("cap-001", 0.93), ENTRY);
      assertThat(t.getEntryMethod()).isEqualTo(Transaction.METHOD_CAMERA_AI);
      assertThat(t.getEntryOperatorId()).isNull();
      assertThat(t.getEntryCaptureId()).isEqualTo("cap-001");
      assertThat(t.getEntryPhotoUrl()).isEqualTo("https://storage/entrada.jpg");
      assertThat(t.getEntryPlateConfidence()).isEqualTo(0.93);
   }

   @Test
   @DisplayName("rechaza datos incoherentes con el método o el documento")
   void rejectsInvalidEntries() {
      // Manual sin operador
      assertThatThrownBy(() -> Transaction.registerEntry(manual(null, null, null, null), ENTRY))
            .isInstanceOf(ParkingDomainException.class).hasMessageContaining("operador");
      // Cámara sin captura o sin confianza, o con confianza fuera de rango
      assertThatThrownBy(() -> Transaction.registerEntry(camera(null, 0.9), ENTRY))
            .hasMessageContaining("captura");
      assertThatThrownBy(() -> Transaction.registerEntry(camera("cap", null), ENTRY))
            .hasMessageContaining("confianza");
      assertThatThrownBy(() -> Transaction.registerEntry(camera("cap", 1.5), ENTRY))
            .hasMessageContaining("entre 0 y 1");
      // Tipo de documento sin número, y documento sin cliente
      assertThatThrownBy(() -> Transaction.registerEntry(manual(null, DNI, null, 7L), ENTRY))
            .hasMessageContaining("juntos");
      assertThatThrownBy(() -> Transaction.registerEntry(manual(null, DNI, "12345678", 7L), ENTRY))
            .hasMessageContaining("cliente");
      // Cliente sin documento
      assertThatThrownBy(() -> Transaction.registerEntry(manual(5L, null, null, 7L), ENTRY))
            .hasMessageContaining("Sin documento");
      // Método desconocido
      assertThatThrownBy(() -> Transaction.registerEntry(new Transaction.EntryData(10L, null, 1L, 2L, 3L, 4L,
            null, null, "SENSOR", 7L, null, null, null), ENTRY)).hasMessageContaining("Método");
      // Falta el espacio
      assertThatThrownBy(() -> Transaction.registerEntry(new Transaction.EntryData(10L, null, 1L, 2L, null, 4L,
            null, null, Transaction.METHOD_MANUAL, 7L, null, null, null), ENTRY)).hasMessageContaining("espacio");
   }

   // ========================= SALIDA =========================

   @Test
   @DisplayName("interruptor apagado: sale por placa aunque haya entrado con documento")
   void exitWithoutDocumentWhenPolicyOff() {
      Transaction t = active(manual(5L, DNI, "12345678", 7L));
      t.completeExit(Transaction.ExitData.manual(null, null, 8L), rate, ENTRY.plusMinutes(3), WINDOW, false);
      assertThat(t.isCompleted()).isTrue();
      assertThat(t.getExitOperatorId()).isEqualTo(8L);
      assertThat(t.getExitDocumentNumber()).isNull();
   }

   @Test
   @DisplayName("interruptor encendido: exige el mismo documento con el que entró")
   void exitRequiresSameDocumentWhenPolicyOn() {
      Transaction t = active(manual(5L, DNI, "12345678", 7L));

      assertThatThrownBy(() -> t.completeExit(Transaction.ExitData.manual(null, null, 8L),
            rate, ENTRY.plusMinutes(3), WINDOW, true)).isInstanceOf(DocumentMismatchException.class);
      assertThatThrownBy(() -> t.completeExit(Transaction.ExitData.manual(DNI, "87654321", 8L),
            rate, ENTRY.plusMinutes(3), WINDOW, true)).isInstanceOf(DocumentMismatchException.class);
      assertThat(t.isActive()).isTrue();

      t.completeExit(Transaction.ExitData.manual(DNI, "12345678", 8L), rate, ENTRY.plusMinutes(3), WINDOW, true);
      assertThat(t.isCompleted()).isTrue();
   }

   @Test
   @DisplayName("el aviso de documento distinto no revela el documento de entrada")
   void mismatchMessageDoesNotLeakDocument() {
      Transaction t = active(manual(5L, DNI, "12345678", 7L));
      assertThatThrownBy(() -> t.completeExit(Transaction.ExitData.manual(DNI, "87654321", 8L),
            rate, ENTRY.plusMinutes(3), WINDOW, true))
            .isInstanceOf(DocumentMismatchException.class)
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain("12345678").doesNotContain("87654321"));
   }

   @Test
   @DisplayName("interruptor encendido pero entró sin documento: sale por placa")
   void exitByPlateWhenEnteredWithoutDocument() {
      Transaction t = active(camera("cap-001", 0.95));
      t.completeExit(new Transaction.ExitData(null, null, Transaction.METHOD_CAMERA_AI, null,
            "cap-002", "https://storage/salida.jpg", 0.91), rate, ENTRY.plusMinutes(3), WINDOW, true);
      assertThat(t.isCompleted()).isTrue();
      assertThat(t.getExitMethod()).isEqualTo(Transaction.METHOD_CAMERA_AI);
      assertThat(t.getExitCaptureId()).isEqualTo("cap-002");
      assertThat(t.getExitPlateConfidence()).isEqualTo(0.91);
   }

   @Test
   @DisplayName("anti-robo se comprueba antes del pago, y sin pagar no sale")
   void documentCheckedBeforePayment() {
      Transaction t = active(manual(5L, DNI, "12345678", 7L));
      LocalDateTime later = ENTRY.plusMinutes(30);
      assertThatThrownBy(() -> t.completeExit(Transaction.ExitData.manual(DNI, "11111111", 8L),
            rate, later, WINDOW, true)).isInstanceOf(DocumentMismatchException.class);
      assertThatThrownBy(() -> t.completeExit(Transaction.ExitData.manual(DNI, "12345678", 8L),
            rate, later, WINDOW, true)).isInstanceOf(PaymentRequiredException.class);
      assertThat(t.isActive()).isTrue();
   }

   @Test
   @DisplayName("una estadía que ya salió no vuelve a salir")
   void cannotExitTwice() {
      Transaction t = active(manual(null, null, null, 7L));
      t.completeExit(Transaction.ExitData.manual(null, null, 8L), rate, ENTRY.plusMinutes(3), WINDOW, false);
      assertThatThrownBy(() -> t.completeExit(Transaction.ExitData.manual(null, null, 8L),
            rate, ENTRY.plusMinutes(4), WINDOW, false)).hasMessageContaining("ya no está en curso");
   }

   // ========================= CAMBIO DE ESPACIO =========================

   @Test
   @DisplayName("cambia el espacio de una estadía en curso y devuelve el anterior")
   void reassignSpace() {
      Transaction t = active(manual(null, null, null, 7L));
      assertThat(t.reassignSpace(30L, 7L)).isEqualTo(3L);
      assertThat(t.getSpaceId()).isEqualTo(30L);
      assertThatThrownBy(() -> t.reassignSpace(30L, 7L)).hasMessageContaining("ya está en ese espacio");

      t.completeExit(Transaction.ExitData.manual(null, null, 8L), rate, ENTRY.plusMinutes(3), WINDOW, false);
      assertThatThrownBy(() -> t.reassignSpace(31L, 7L)).hasMessageContaining("en curso");
   }
}
