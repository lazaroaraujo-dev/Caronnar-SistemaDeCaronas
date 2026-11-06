package com.lazaro.caronnar.exception;

public class IdempotencyPayloadMismatchException extends RuntimeException {

    public IdempotencyPayloadMismatchException(String mensagem) {
        super(mensagem);
    }
}
