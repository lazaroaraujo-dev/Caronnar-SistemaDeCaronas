package com.lazaro.caronnar.exception;

public class RotaNaoEncontradaException extends RuntimeException {

    public RotaNaoEncontradaException(String mensagem) {
        super(mensagem);
    }

    public RotaNaoEncontradaException(String mensagem, Throwable cause) {
        super(mensagem, cause);
    }
}
