package com.lazaro.caronnar.exception;

public class CoordenadasInvalidasException extends RuntimeException {

    public CoordenadasInvalidasException(String mensagem) {
        super(mensagem);
    }

    public CoordenadasInvalidasException(String mensagem, Throwable cause) {
        super(mensagem, cause);
    }
}
