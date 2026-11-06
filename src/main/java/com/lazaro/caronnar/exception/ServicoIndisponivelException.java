package com.lazaro.caronnar.exception;

public class ServicoIndisponivelException extends RuntimeException {

    public ServicoIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public ServicoIndisponivelException(String mensagem, Throwable cause) {
        super(mensagem, cause);
    }
}
