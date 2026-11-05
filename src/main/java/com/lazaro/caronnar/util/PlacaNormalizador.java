package com.lazaro.caronnar.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PlacaNormalizador {

    private static final Pattern PADRAO_ANTIGO = Pattern.compile("^\\s*[A-Za-z]{3}-?[0-9]{4}\\s*$");
    private static final Pattern PADRAO_MERCOSUL = Pattern.compile("^\\s*[A-Za-z]{3}-?[0-9][A-Za-z][0-9]{2}\\s*$");

    private static final char[] DIGITO_PARA_LETRA_MERCOSUL = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'
    };

    private PlacaNormalizador() {
    }

    public static boolean isFormatoValido(String placa) {
        if (placa == null) {
            return false;
        }
        return isPadraoAntigo(placa) || isPadraoMercosul(placa);
    }

    public static boolean isPadraoAntigo(String placa) {
        return placa != null && PADRAO_ANTIGO.matcher(placa).matches();
    }

    public static boolean isPadraoMercosul(String placa) {
        return placa != null && PADRAO_MERCOSUL.matcher(placa).matches();
    }

    public static String normalizar(String placa) {
        if (placa == null) {
            return null;
        }
        return placa.trim().replace("-", "").toUpperCase(Locale.ROOT);
    }

    public static String gerarChaveCanonica(String placaBruta) {
        if (placaBruta == null) {
            return null;
        }

        String normalizada = normalizar(placaBruta);

        if (isPadraoAntigo(placaBruta)) {
            char digito = normalizada.charAt(4);
            char letraMercosul = DIGITO_PARA_LETRA_MERCOSUL[digito - '0'];
            return "placa::" + normalizada.substring(0, 4) + letraMercosul + normalizada.substring(5, 7);
        }

        if (isPadraoMercosul(placaBruta)) {
            return "placa::" + normalizada;
        }

        throw new IllegalArgumentException("Placa fora dos padrões brasileiro antigo ou Mercosul: " + placaBruta);
    }
}
