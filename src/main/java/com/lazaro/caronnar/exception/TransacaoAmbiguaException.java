package com.lazaro.caronnar.exception;

public class TransacaoAmbiguaException extends RuntimeException {

    public TransacaoAmbiguaException(String mensagem) {
        super(mensagem);
    }

    public TransacaoAmbiguaException(String mensagem, Throwable cause) {
        super(mensagem, cause);
    }
}
