package com.lazaro.caronnar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class OsrmConfig {

    @Bean
    public RestClient osrmRestClient(
            @Value("${app.osrm.base-url:http://router.project-osrm.org}") String baseUrl,
            @Value("${app.osrm.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${app.osrm.read-timeout-ms:5000}") long readTimeoutMs) {

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("A propriedade app.osrm.base-url (OSRM_BASE_URL) não pode ser nula ou vazia.");
        }
        if (connectTimeoutMs <= 0) {
            throw new IllegalStateException("A propriedade app.osrm.connect-timeout-ms (OSRM_CONNECT_TIMEOUT_MS) deve ser maior que zero.");
        }
        if (readTimeoutMs <= 0) {
            throw new IllegalStateException("A propriedade app.osrm.read-timeout-ms (OSRM_READ_TIMEOUT_MS) deve ser maior que zero.");
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl.trim())
                .requestFactory(requestFactory)
                .build();
    }
}
