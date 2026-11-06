package com.lazaro.caronnar.exception;

public class UsuarioNaoAutenticadoException extends RuntimeException {

    public UsuarioNaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
