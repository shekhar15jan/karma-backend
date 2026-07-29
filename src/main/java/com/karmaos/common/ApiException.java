package com.karmaos.common;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object details;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    public ApiException(ErrorCode errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
    }
}
