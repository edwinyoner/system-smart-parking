package com.winnersystems.smartparking.payment.infrastructure.adapter.output.client;

import com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto.ServiceTokenRequest;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto.ServiceTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** auth-service por la red interna (Eureka), sin pasar por el gateway */
@FeignClient(name = "auth-service", contextId = "authServiceTokenClient")
public interface AuthServiceClient {

   @PostMapping("/service-token")
   ServiceTokenResponse issueServiceToken(@RequestBody ServiceTokenRequest request);
}
