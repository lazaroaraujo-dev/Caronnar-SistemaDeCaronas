package com.lazaro.caronnar.exception;

public class ConflitoIdempotenciaException extends RuntimeException {

    public ConflitoIdempotenciaException(String message) {
        super(message);
    }

    public ConflitoIdempotenciaException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConflitoIdempotenciaException(Throwable cause) {
        super("Conflito detectado na inserção da chave de idempotência.", cause);
    }
}
