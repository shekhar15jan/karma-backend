package com.karmaos.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST, 4000, "Bad request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 4001, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, 4003, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, 4004, "Resource not found"),
    CONFLICT(HttpStatus.CONFLICT, 4009, "Resource conflict"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, 4100, "Validation error"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, 4101, "Invalid credentials"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, 4102, "Token expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, 4103, "Token invalid"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, 4201, "Email already registered"),
    DUPLICATE_NAME(HttpStatus.CONFLICT, 4202, "Name already exists"),
    WORKSPACE_LIMIT(HttpStatus.FORBIDDEN, 4301, "Workspace limit reached"),
    PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, 4401, "AI provider error"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, 4501, "Rate limited"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 5000, "Internal server error");

    private final HttpStatus httpStatus;
    private final int code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, int code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public int getCode() { return code; }
    public String getDefaultMessage() { return defaultMessage; }

    public ApiException exception() {
        return new ApiException(this, defaultMessage);
    }

    public ApiException exception(String message) {
        return new ApiException(this, message);
    }
}
