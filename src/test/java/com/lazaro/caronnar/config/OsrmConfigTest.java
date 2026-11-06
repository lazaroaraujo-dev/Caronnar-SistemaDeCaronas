package com.lazaro.caronnar.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OsrmConfigTest {

    private final OsrmConfig osrmConfig = new OsrmConfig();

    @Test
    void deveCriarRestClientComConfiguracaoValida() {
        RestClient client = osrmConfig.osrmRestClient("http://router.project-osrm.org", 3000, 5000);
        assertNotNull(client);
    }

    @Test
    void deveRejeitarBaseUrlNula() {
        assertThrows(IllegalStateException.class,
                () -> osrmConfig.osrmRestClient(null, 3000, 5000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void deveRejeitarBaseUrlVaziaOuEmBranco(String urlInvalida) {
        assertThrows(IllegalStateException.class,
                () -> osrmConfig.osrmRestClient(urlInvalida, 3000, 5000));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -3000})
    void deveRejeitarConnectTimeoutMenorOuIgualAZero(long connectTimeoutInvalido) {
        assertThrows(IllegalStateException.class,
                () -> osrmConfig.osrmRestClient("http://router.project-osrm.org", connectTimeoutInvalido, 5000));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -5000})
    void deveRejeitarReadTimeoutMenorOuIgualAZero(long readTimeoutInvalido) {
        assertThrows(IllegalStateException.class,
                () -> osrmConfig.osrmRestClient("http://router.project-osrm.org", 3000, readTimeoutInvalido));
    }
}
