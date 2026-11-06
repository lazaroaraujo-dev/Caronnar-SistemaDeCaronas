package com.lazaro.caronnar.exception;

public class PlacaJaCadastradaException extends RuntimeException {

    public PlacaJaCadastradaException() {
        super("Já existe um veículo cadastrado com esta placa.");
    }

    public PlacaJaCadastradaException(String message) {
        super(message);
    }

    public PlacaJaCadastradaException(String message, Throwable cause) {
        super(message, cause);
    }

    public PlacaJaCadastradaException(Throwable cause) {
        super("Já existe um veículo cadastrado com esta placa.", cause);
    }
}
