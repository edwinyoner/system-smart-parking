package com.winnersystems.smartparking.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Informa a los servicios internos la IP real del cliente.
 *
 * El gateway es la primera pieza que recibe el tráfico, así que la dirección de la conexión
 * es la del cliente (ESP32, app o navegador). Las cabeceras de IP que envíe el propio cliente
 * se descartan: si se reenviaran, cualquiera podría falsear su IP y saltarse los límites de
 * peticiones de iot-service, que cuenta por IP cuando el dispositivo no envía X-Device-ID.
 *
 * Si algún día se pone un proxy delante del gateway (por ejemplo, nginx), habrá que confiar
 * en la cabecera que añada ese proxy en lugar de usar la dirección de la conexión.
 */
@Component
public class ClientIpFilter implements GlobalFilter, Ordered {

   static final String FORWARDED_FOR = "X-Forwarded-For";
   static final String REAL_IP = "X-Real-IP";

   @Override
   public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
      InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
      String clientIp = remote != null && remote.getAddress() != null
            ? remote.getAddress().getHostAddress()
            : null;

      ServerHttpRequest request = exchange.getRequest().mutate()
            .headers(headers -> {
               // Lo que diga el cliente sobre su IP no se tiene en cuenta
               headers.remove(FORWARDED_FOR);
               headers.remove(REAL_IP);
               headers.remove("Forwarded");
               if (clientIp != null) {
                  headers.set(FORWARDED_FOR, clientIp);
                  headers.set(REAL_IP, clientIp);
               }
            })
            .build();

      return chain.filter(exchange.mutate().request(request).build());
   }

   /** Antes que el resto de filtros, para que todos vean la IP correcta */
   @Override
   public int getOrder() {
      return Ordered.HIGHEST_PRECEDENCE;
   }
}
