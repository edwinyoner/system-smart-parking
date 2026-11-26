package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.advice;

import com.winnersystems.smartparking.payment.domain.exception.ExternalServiceException;
import com.winnersystems.smartparking.payment.domain.exception.PaymentDomainException;
import com.winnersystems.smartparking.payment.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.payment.domain.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convierte las excepciones en respuestas JSON con el mismo formato que parking-service:
 * 400 regla de negocio o validación, 403 sin permiso, 404 no existe, 409 conflicto, 500 inesperado.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

   public record ErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path) {
   }

   public record ValidationErrorResponse(LocalDateTime timestamp, int status, String error, String message,
                                         String path, Map<String, String> errors) {
   }

   @ExceptionHandler(ResourceNotFoundException.class)
   public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
      return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
   }

   @ExceptionHandler(ResourceConflictException.class)
   public ResponseEntity<ErrorResponse> handleConflict(ResourceConflictException ex, WebRequest request) {
      return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
   }

   /** parking-service o auth-service no respondieron: el pago no se registró, se puede reintentar */
   @ExceptionHandler(ExternalServiceException.class)
   public ResponseEntity<ErrorResponse> handleExternal(ExternalServiceException ex, WebRequest request) {
      log.warn("Servicio externo no disponible: {}", ex.getMessage());
      return build(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
            "No se pudo consultar el cobro en este momento. Intenta de nuevo en unos segundos.", request);
   }

   @ExceptionHandler(PaymentDomainException.class)
   public ResponseEntity<ErrorResponse> handleDomain(PaymentDomainException ex, WebRequest request) {
      return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
   }

   @ExceptionHandler(MethodArgumentNotValidException.class)
   public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
      Map<String, String> errors = new LinkedHashMap<>();
      ex.getBindingResult().getAllErrors().forEach(error -> {
         String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
         errors.putIfAbsent(field, error.getDefaultMessage());
      });
      return ResponseEntity.badRequest().body(new ValidationErrorResponse(
            LocalDateTime.now(), 400, "Validation Failed", "Los datos enviados no son válidos", path(request), errors));
   }

   /** JSON mal formado o un valor que no existe (por ejemplo, un canal desconocido) */
   @ExceptionHandler(HttpMessageNotReadableException.class)
   public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, WebRequest request) {
      return build(HttpStatus.BAD_REQUEST, "Bad Request", "El cuerpo de la petición no es válido", request);
   }

   /** Un @PreAuthorize denegado: sin este manejador terminaría en 500 */
   @ExceptionHandler(AccessDeniedException.class)
   public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
      return build(HttpStatus.FORBIDDEN, "Forbidden", "No tienes permiso para realizar esta acción", request);
   }

   @ExceptionHandler(Exception.class)
   public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, WebRequest request) {
      log.error("Error no controlado en {}", request.getDescription(false), ex);
      return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
            "Ocurrió un error inesperado. Por favor, contacte al administrador.", request);
   }

   private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message, WebRequest request) {
      return ResponseEntity.status(status)
            .body(new ErrorResponse(LocalDateTime.now(), status.value(), error, message, path(request)));
   }

   private static String path(WebRequest request) {
      return request.getDescription(false).replace("uri=", "");
   }
}
