package com.lazaro.caronnar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    public static final String ZONA_OFICIAL_PADRAO = "America/Sao_Paulo";
    private static final ZoneId ZONA_OFICIAL = ZoneId.of(ZONA_OFICIAL_PADRAO);

    @Bean
    public ZoneId appZoneId(@Value("${app.timezone:America/Sao_Paulo}") String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new IllegalStateException("A propriedade app.timezone (APP_TIMEZONE) não pode ser nula ou vazia.");
        }
        final ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone.trim());
        } catch (DateTimeException e) {
            throw new IllegalStateException("A propriedade app.timezone (APP_TIMEZONE) contém um ZoneId inválido: '" + timezone + "'.", e);
        }

        if (!ZONA_OFICIAL.equals(zoneId)) {
            throw new IllegalStateException(
                    "Zona configurada inválida: app.timezone está definido como '" + zoneId
                            + "', mas a zona oficial obrigatória da aplicação é '" + ZONA_OFICIAL_PADRAO + "'.");
        }

        ZoneId jvmZoneId = ZoneId.systemDefault();
        if (!ZONA_OFICIAL.equals(jvmZoneId)) {
            throw new IllegalStateException(
                    "Inconsistência de fuso horário na JVM: ZoneId.systemDefault() é '" + jvmZoneId
                            + "', mas deve ser '" + ZONA_OFICIAL_PADRAO
                            + "' para coincidir com a zona oficial da aplicação e com o MappingCouchbaseConverter.");
        }

        return zoneId;
    }

    @Bean
    public Clock appClock(ZoneId appZoneId) {
        if (appZoneId == null || !ZONA_OFICIAL.equals(appZoneId)) {
            throw new IllegalStateException(
                    "O Clock da aplicação exige a zona oficial '" + ZONA_OFICIAL_PADRAO + "'.");
        }
        return Clock.system(appZoneId);
    }
}
