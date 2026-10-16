package com.soukline.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception lancée lorsque le mode panne est activé sur /checkout.
 * Déclenche un code HTTP 503 Service Unavailable.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class PanneSimuleeException extends RuntimeException {

    public PanneSimuleeException(String message) {
        super(message);
    }

    public PanneSimuleeException(String message, Throwable cause) {
        super(message, cause);
    }
}
