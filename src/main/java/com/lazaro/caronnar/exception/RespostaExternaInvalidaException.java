package com.lazaro.caronnar.exception;

public class RespostaExternaInvalidaException extends RuntimeException {

    public RespostaExternaInvalidaException(String mensagem) {
        super(mensagem);
    }

    public RespostaExternaInvalidaException(String mensagem, Throwable cause) {
        super(mensagem, cause);
    }
}
