package com.winnersystems.smartparking.parking.application.port.input.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.CameraCaptureCommand;
import com.winnersystems.smartparking.parking.application.dto.query.CameraCaptureResult;

/**
 * Puerto de entrada: lectura de una cámara de entrada o salida (la envía iot-service).
 *
 * - Confianza >= parking.ai.min-plate-confidence (0.85) y placa válida: registra la entrada
 *   o la salida con las mismas reglas que el operador.
 * - Si no: abre una revisión para el operador (PENDING_REVIEW).
 * - Es idempotente por captureId: si la cámara reenvía la misma captura, responde lo mismo
 *   sin crear otra estadía ni otra revisión.
 *
 * No lanza excepciones por reglas de negocio: devuelve un resultado que el dispositivo entiende
 * (abrir barrera, cobrar, esperar o rechazar con un motivo).
 */
public interface ProcessCameraCaptureUseCase {

   CameraCaptureResult processCapture(CameraCaptureCommand command);
}
