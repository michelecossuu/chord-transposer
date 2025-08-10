package com.michelecossu.chords.transposer.web.exception;

public class TargetKeyException extends RuntimeException {
    public TargetKeyException(String message) {
        super(message);
    }

    public TargetKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
