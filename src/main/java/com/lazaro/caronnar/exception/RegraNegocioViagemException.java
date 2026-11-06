package com.lazaro.caronnar.exception;

public class RegraNegocioViagemException extends RuntimeException {

    public RegraNegocioViagemException(String mensagem) {
        super(mensagem);
    }

    public RegraNegocioViagemException(String mensagem, Throwable cause) {
        super(mensagem, cause);
    }
}
