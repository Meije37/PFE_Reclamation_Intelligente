package com.pfe.backend.exception;

public class FichierInvalideException extends RuntimeException {
    public FichierInvalideException(String message) {
        super(message);
    }
}