package com.lazaro.caronnar.exception;

public class DataPartidaInvalidaException extends RuntimeException {

    public DataPartidaInvalidaException(String mensagem) {
        super(mensagem);
    }

    public DataPartidaInvalidaException(String mensagem, Throwable cause) {
        super(mensagem, cause);
    }
}
