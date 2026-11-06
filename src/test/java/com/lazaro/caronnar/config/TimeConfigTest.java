package com.lazaro.caronnar.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeConfigTest {

    private TimeZone fusoOriginalJvm;
    private TimeConfig timeConfig;

    @BeforeEach
    void setUp() {
        fusoOriginalJvm = TimeZone.getDefault();
        timeConfig = new TimeConfig();
    }

    @AfterEach
    void tearDown() {
        if (fusoOriginalJvm != null) {
            TimeZone.setDefault(fusoOriginalJvm);
        }
    }

    @Test
    void deveConfigurarZonaEClockCorretamenteComAmericaSaoPaulo() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));

        ZoneId zoneId = timeConfig.appZoneId("America/Sao_Paulo");
        Clock clock = timeConfig.appClock(zoneId);

        assertEquals(ZoneId.of("America/Sao_Paulo"), zoneId);
        assertNotNull(clock);
        assertEquals(ZoneId.of("America/Sao_Paulo"), clock.getZone());
        assertEquals(ZoneId.systemDefault(), clock.getZone());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Manaus", "America/New_York", "Europe/Lisbon", "-03:00"})
    void deveRejeitarZonaConfiguradaDiferenteDaZonaOficialMesmoComJvmEmAmericaSaoPaulo(String zonaConfigurada) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> timeConfig.appZoneId(zonaConfigurada)
        );

        assertTrue(ex.getMessage().contains("America/Sao_Paulo"));
    }

    @Test
    void deveRejeitarZonaConfiguradaUtcMesmoQuandoJvmTambemEstiverEmUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> timeConfig.appZoneId("UTC")
        );

        assertTrue(ex.getMessage().contains("America/Sao_Paulo"));
        assertTrue(ex.getMessage().contains("UTC"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Manaus", "Europe/London"})
    void deveRejeitarQuandoFusoPadraoDaJvmNaoCorrespondeAZonaOficial(String fusoJvm) {
        TimeZone.setDefault(TimeZone.getTimeZone(fusoJvm));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> timeConfig.appZoneId("America/Sao_Paulo")
        );

        assertTrue(ex.getMessage().contains("America/Sao_Paulo"));
        assertTrue(ex.getMessage().contains(fusoJvm));
    }

    @Test
    void deveRejeitarZonaConfiguradaNulaVaziaOuInvalida() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));

        assertThrows(IllegalStateException.class, () -> timeConfig.appZoneId(null));
        assertThrows(IllegalStateException.class, () -> timeConfig.appZoneId("   "));
        assertThrows(IllegalStateException.class, () -> timeConfig.appZoneId("Zona/Inexistente"));
    }

    @Test
    void deveRejeitarCriacaoDeClockComZonaDiferenteDaOficial() {
        ZoneId zonaUtc = ZoneId.of("UTC");
        assertThrows(IllegalStateException.class, () -> timeConfig.appClock(null));
        assertThrows(IllegalStateException.class, () -> timeConfig.appClock(zonaUtc));
    }
}
