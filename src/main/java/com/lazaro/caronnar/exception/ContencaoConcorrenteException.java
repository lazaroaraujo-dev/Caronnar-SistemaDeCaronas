package com.lazaro.caronnar.exception;

public class ContencaoConcorrenteException extends RuntimeException {

    public ContencaoConcorrenteException(String mensagem) {
        super(mensagem);
    }

    public ContencaoConcorrenteException(String mensagem, Throwable cause) {
        super(mensagem, cause);
    }
}
