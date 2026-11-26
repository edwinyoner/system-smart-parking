package com.winnersystems.smartparking.payment.infrastructure.adapter.output.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Firma de los avisos de la pasarela: HMAC-SHA256 del cuerpo exacto con un secreto compartido.
 * Cabecera: X-Gateway-Signature: sha256=&lt;hex&gt;
 *
 * Así solo la pasarela, que conoce el secreto, puede marcar un pago como pagado: cualquiera puede
 * llamar a la URL del webhook, pero sin la firma correcta el aviso se rechaza.
 * Sin secreto (o con menos de 32 caracteres) se rechazan todos los avisos.
 */
@Slf4j
@Component
public class WebhookSignature {

   public static final String HEADER = "X-Gateway-Signature";
   private static final String PREFIX = "sha256=";
   private static final int MIN_SECRET_LENGTH = 32;

   private final byte[] secret;

   public WebhookSignature(@Value("${payment.gateway.webhook-secret:}") String secret) {
      this.secret = secret.getBytes(StandardCharsets.UTF_8);
      if (secret.length() < MIN_SECRET_LENGTH) {
         log.warn("Falta PAYMENT_GATEWAY_WEBHOOK_SECRET (mínimo {} caracteres): se rechazarán los avisos de la pasarela",
               MIN_SECRET_LENGTH);
      }
   }

   public boolean isConfigured() {
      return secret.length >= MIN_SECRET_LENGTH;
   }

   /** Valor de la cabecera para un cuerpo */
   public String sign(String body) {
      if (!isConfigured()) {
         throw new IllegalStateException("Secreto del webhook no configurado");
      }
      return PREFIX + HexFormat.of().formatHex(hmac(body));
   }

   /** Compara en tiempo constante: no revela cuántos caracteres coinciden */
   public boolean verify(String body, String header) {
      if (!isConfigured() || body == null || header == null || !header.startsWith(PREFIX)) {
         return false;
      }
      byte[] received;
      try {
         received = HexFormat.of().parseHex(header.substring(PREFIX.length()));
      } catch (IllegalArgumentException e) {
         return false;
      }
      return MessageDigest.isEqual(hmac(body), received);
   }

   private byte[] hmac(String body) {
      try {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(new SecretKeySpec(secret, "HmacSHA256"));
         return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      } catch (GeneralSecurityException e) {
         throw new IllegalStateException("HmacSHA256 no disponible", e);
      }
   }
}
