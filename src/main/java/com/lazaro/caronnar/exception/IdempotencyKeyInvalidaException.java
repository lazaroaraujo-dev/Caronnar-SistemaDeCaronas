package com.lazaro.caronnar.exception;

public class IdempotencyKeyInvalidaException extends RuntimeException {

    public IdempotencyKeyInvalidaException(String mensagem) {
        super(mensagem);
    }
}
